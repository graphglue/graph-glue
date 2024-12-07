package io.github.graphglue.definition

import io.github.graphglue.authorization.MergedAuthorization
import io.github.graphglue.definition.extensions.firstTypeArgument
import io.github.graphglue.graphql.extensions.*
import io.github.graphglue.model.*
import io.github.graphglue.model.property.NODE_PROPERTY_TYPE
import io.github.graphglue.model.property.NODE_SET_PROPERTY_TYPE
import io.github.graphglue.model.property.NodePropertyDelegate
import org.neo4j.cypherdsl.core.Cypher
import org.neo4j.cypherdsl.core.Expression
import org.neo4j.cypherdsl.core.LabelExpression
import org.neo4j.cypherdsl.core.SymbolicName
import org.springframework.data.neo4j.core.mapping.Constants
import org.springframework.data.neo4j.core.mapping.CypherGenerator
import org.springframework.data.neo4j.core.mapping.Neo4jPersistentEntity
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure

/**
 * Definition of a [Node]
 * Used to find relationships, get the primary label, create a Cypher-DSL return expressions
 *
 * @param nodeType the class associated with the definition
 * @param persistentEntity defines Neo4j's view on the node
 * @param extensionFieldDefinitions all known [ExtensionFieldDefinition] beans
 */
class NodeDefinition(
    val nodeType: KClass<out Node>,
    val persistentEntity: Neo4jPersistentEntity<*>,
    extensionFieldDefinitions: Map<String, ExtensionFieldDefinition>
) {
    /**
     * map of all found authorizations defined on this type from authorization name to [Authorization]
     */
    val authorizations: Map<String, MergedAuthorization> = generateAuthorizations()

    /**
     * map of all found authorizations defined on this type and all supertypes from authorization name to [Authorization]
     */
    val mergedAuthorizations: Map<String, MergedAuthorization> = generateMergedAuthorizations()

    /**
     * All one [RelationshipDefinition]s
     */
    private val oneRelationshipDefinitions: List<OneRelationshipDefinition> = generateOneRelationshipDefinitions()

    /**
     * All many [RelationshipDefinition]s
     */
    private val manyRelationshipDefinitions: List<ManyRelationshipDefinition> = generateManyRelationshipDefinitions()

    /**
     * List of all relationship definitions by GraphQL name
     */
    val relationshipDefinitions = oneRelationshipDefinitions + manyRelationshipDefinitions

    /**
     * All [FieldDefinition]s of this [NodeDefinition]
     */
    val fieldDefinitions =
        generateExtensionFieldDefinitions(extensionFieldDefinitions) + oneRelationshipDefinitions.map {
            OneRelationshipFieldDefinition(it)
        } + manyRelationshipDefinitions.map {
            ManyRelationshipFieldDefinition(it)
        } + generateAggregatedRelationshipFieldDefinitions()

    /**
     * Map of all field definitions by property
     */
    private val fieldDefinitionByProperty =
        fieldDefinitions.filter { it.property != null }.associateBy { it.property!!.name }

    /**
     * Map of all field definitions by GraphQL name
     */
    private val fieldDefinitionByGraphQLName = fieldDefinitions.associateBy { it.graphQLName }

    /**
     * map of all relationship definitions by defining property
     * Name of property as key
     */
    internal val relationshipDefinitionsByProperty =
        (oneRelationshipDefinitions + manyRelationshipDefinitions).associateBy { it.property.name }

    /**
     * Lookup for [RelationshipDefinition]s by its inverse
     */
    private val relationshipDefinitionByInverse: MutableMap<RelationshipDefinition, RelationshipDefinition?> =
        mutableMapOf()

    /**
     * Expression which can be used when creating a query using Cypher-DSL
     * Fetches all necessary data to map the result to a [Node]
     */
    val returnExpression: Expression

    /**
     * Name of the return value in the [returnExpression]
     */
    val returnNodeName: SymbolicName

    /**
     * The primary label of the [Node]
     */
    val primaryLabel: String get() = persistentEntity.primaryLabel

    /**
     * GraphQL type name
     */
    val name get() = nodeType.getSimpleName()

    /**
     * The name of the search index, if existing
     */
    val searchIndexName: String?

    init {
        val expressions = CypherGenerator.INSTANCE.createReturnStatementForMatch(persistentEntity)
        if (expressions.size != 1) {
            throw IllegalStateException("Cannot get return expression for $nodeType, probably due to cyclic references: $expressions")
        }
        returnExpression = expressions.first()
        returnNodeName = Constants.NAME_OF_TYPED_ROOT_NODE.apply(persistentEntity)
        searchIndexName = if (nodeType.springFindAnnotation<DomainNode>()?.searchQueryName?.isNotBlank() == true) {
            "${name}SearchIndex"
        } else {
            null
        }
    }

    /**
     * Creates all authorizations by search for [Authorization] annotations on [nodeType] and all super types
     *
     * @return the map of found merged authorizations by authorization name
     */
    private fun generateMergedAuthorizations(): Map<String, MergedAuthorization> {
        val allAuthorizations = nodeType.springFindRepeatableAnnotations<Authorization>()
        return mergeAuthorizations(allAuthorizations)
    }

    /**
     * Creates all authorizations by search for [Authorization] annotations on [nodeType]
     *
     * @return the map of found merged authorizations by authorization name
     */
    private fun generateAuthorizations(): Map<String, MergedAuthorization> {
        val allAuthorizations = nodeType.springGetRepeatableAnnotations<Authorization>()
        return mergeAuthorizations(allAuthorizations)
    }

    /**
     * Merges a list of [Authorization]s
     *
     * @return the merged [Authorization]
     */
    private fun mergeAuthorizations(authorizations: Collection<Authorization>) =
        authorizations.groupBy { it.name }.mapValues { (name, authorizations) ->
            val authorization = MergedAuthorization(
                name,
                authorizations.flatMap { it.allow.toList() }.toSet(),
                authorizations.flatMap { it.allowFromRelated.toList() }.toSet(),
                authorizations.flatMap { it.disallow.toList() }.toSet(),
                authorizations.any { it.allowAll })
            authorization
        }


    /**
     * Finds the [ExtensionFieldDefinition]s for this [NodeDefinition] from the provided list of all definitions
     *
     * @param extensionFieldGenerators all known [ExtensionFieldDefinition] beans
     * @return the list of found [ExtensionFieldDefinition]s
     */
    private fun generateExtensionFieldDefinitions(extensionFieldGenerators: Map<String, ExtensionFieldDefinition>): List<ExtensionFieldDefinition> {
        val allExtensionFields = nodeType.springFindRepeatableAnnotations<ExtensionField>()
        return allExtensionFields.mapNotNull {
            extensionFieldGenerators[it.beanName]
        }
    }

    /**
     * Generates the [AggregatedRelationshipFieldDefinition]s for this [NodeDefinition]
     *
     * @return the list of generated [AggregatedRelationshipFieldDefinition]s
     */
    private fun generateAggregatedRelationshipFieldDefinitions(): List<AggregatedRelationshipFieldDefinition> {
        val allAggregatedRelationships = nodeType.springFindRepeatableAnnotations<AggregatedNodeRelationship>()
        return allAggregatedRelationships.map { relationship ->
            val properties = mutableListOf<PropertyWithOwner>()
            properties += PropertyWithOwner(nodeType.findProperty(relationship.property), nodeType)
            for (property in relationship.path) {
                val currentKClass = getRelationPropertyType(properties.last().property)
                val propertyOwner = if (property.subclass == Node::class) {
                    currentKClass
                } else {
                    val selectedKClass = property.subclass
                    if (!selectedKClass.isSubclassOf(currentKClass)) {
                        throw NodeSchemaException("Class $selectedKClass is not a subclass of the property type class $currentKClass of the previous property in the path")
                    }
                    selectedKClass
                }
                properties += PropertyWithOwner(propertyOwner.findProperty(property.property), propertyOwner)
            }
            AggregatedRelationshipFieldDefinition(
                relationship.name, relationship.description, properties
            )
        }
    }

    /**
     * Gets the node type of a NodeProperty or NodeSetProperty
     *
     * @param property the property to get the node type of
     * @return the node type of the property
     */
    @Suppress("UNCHECKED_CAST")
    private fun getRelationPropertyType(property: KProperty1<*, *>): KClass<out Node> {
        if (property.returnType.isSubtypeOf(NODE_PROPERTY_TYPE) || property.returnType.isSubtypeOf(
                NODE_SET_PROPERTY_TYPE
            )
        ) {
            return property.returnType.firstTypeArgument.jvmErasure as KClass<out Node>
        } else {
            throw NodeSchemaException("Property used in aggregated relationship is not a NodeProperty or NodeSetProperty: $property")
        }
    }

    /**
     * Generates the [OneRelationshipDefinition]s for this [NodeDefinition]
     *
     * @return the list of generated relationship definitions
     */
    private fun generateOneRelationshipDefinitions(): List<OneRelationshipDefinition> {
        val properties = nodeType.memberProperties.filter { it.returnType.isSubtypeOf(NODE_PROPERTY_TYPE) }
            .filter { it.returnType.firstTypeArgument.classifier !is KTypeParameter }.filter { !it.isAbstract }

        return properties.map {
            val field = it.javaField
            if (field == null || !field.type.kotlin.isSubclassOf(NodePropertyDelegate::class)) {
                throw NodeSchemaException("Property of type Node is not backed by a NodeProperty: $it")
            }
            val annotation = it.findAnnotation<NodeRelationship>()
                ?: throw NodeSchemaException("Property of type Node is not annotated with NodeRelationship: $it")
            OneRelationshipDefinition(
                it, annotation.type, annotation.direction, nodeType, generateRelationshipAuthorizationNames(it)
            )
        }
    }

    /**
     * Generates the [ManyRelationshipDefinition]s for this [NodeDefinition]
     *
     * @return the list of generated relationship definitions
     */
    private fun generateManyRelationshipDefinitions(): List<ManyRelationshipDefinition> {
        val properties = nodeType.memberProperties.filter { it.returnType.isSubtypeOf(NODE_SET_PROPERTY_TYPE) }.filter {
            it.returnType.firstTypeArgument.classifier !is KTypeParameter
        }.filter { !it.isAbstract }
        return properties.map {
            val annotation = it.findAnnotation<NodeRelationship>()
                ?: throw NodeSchemaException("Property of type Node is not annotated with NodeRelationship: $it")
            ManyRelationshipDefinition(
                it, annotation.type, annotation.direction, nodeType, generateRelationshipAuthorizationNames(it)
            )
        }
    }

    /**
     * Gets the set of authorization names which allow via a Relation defined by a property
     *
     * @param property the property defining the relation
     * @return a set with all authorization names allowing via the relation
     */
    private fun generateRelationshipAuthorizationNames(
        property: KProperty1<*, *>
    ): Set<String> {
        return mergedAuthorizations.filterValues {
            it.allowFromRelated.contains(property.name)
        }.keys
    }

    /**
     * Gets the Neo4j name of a property
     *
     * @param property the property to get the name of
     * @return the name used by Neo4j
     */
    fun getNeo4jNameOfProperty(property: KProperty1<*, *>): String {
        return persistentEntity.getGraphProperty(property.name).orElseThrow().propertyName
    }

    /**
     * Gets the [RelationshipDefinition] by property
     *
     * @param property the property to get the relation of
     * @return the found [RelationshipDefinition]
     * @throws Exception if property is not used as relation
     */
    fun getRelationshipDefinitionOfProperty(property: KProperty1<*, *>): RelationshipDefinition {
        return relationshipDefinitionsByProperty[property.name]!!
    }

    /**
     * Gets the [RelationshipDefinition] by property
     *
     * @param property the property to get the relation of
     * @return the found [RelationshipDefinition] or null if none was found
     */
    fun getRelationshipDefinitionOfPropertyOrNull(property: KProperty1<*, *>): RelationshipDefinition? {
        return relationshipDefinitionsByProperty[property.name]
    }

    /**
     * Gets the [FieldDefinition] of a property
     *
     * @param property the property to get the field of
     * @return the found [FieldDefinition]
     */
    fun getFieldDefinitionOfProperty(property: KProperty1<*, *>): FieldDefinition {
        return fieldDefinitionByProperty[property.name]!!
    }

    /**
     * Gets the [FieldDefinition] by the GraphQL name of the field
     *
     * @param graphqlName the name of the field in the GraphQL schema
     * @return the found [FieldDefinition] or null if none was found
     */
    fun getFieldDefinitionOrNull(graphqlName: String): FieldDefinition? {
        return fieldDefinitionByGraphQLName[graphqlName]
    }

    /**
     * Generates a new CypherDSL node with the necessary labels
     */
    fun node(): org.neo4j.cypherdsl.core.Node {
        return Cypher.node(persistentEntity.additionalLabels.fold(LabelExpression(persistentEntity.primaryLabel)) { acc, label ->
            acc.and(LabelExpression(label))
        })
    }

    /**
     * Gets a [RelationshipDefinition] by the [inverse]
     */
    fun getRelationshipDefinitionByInverse(inverse: RelationshipDefinition): RelationshipDefinition? {
        return relationshipDefinitionByInverse.computeIfAbsent(inverse) {
            relationshipDefinitions.firstOrNull {
                it.type == inverse.type && it.direction != inverse.direction && it.nodeKClass.isSuperclassOf(inverse.parentKClass)
            }
        }
    }

    override fun toString() = nodeType.getSimpleName()
}
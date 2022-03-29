package io.github.graphglue.db.execution.definition

import io.github.graphglue.graphql.extensions.getSimpleName
import io.github.graphglue.graphql.extensions.springFindRepeatableAnnotations
import io.github.graphglue.model.Authorization
import io.github.graphglue.model.Node
import io.github.graphglue.db.authorization.MergedAuthorization
import org.neo4j.cypherdsl.core.Expression
import org.neo4j.cypherdsl.core.SymbolicName
import org.springframework.data.neo4j.core.mapping.Constants
import org.springframework.data.neo4j.core.mapping.CypherGenerator
import org.springframework.data.neo4j.core.mapping.Neo4jPersistentEntity
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * Definition of a [Node]
 * Used to find relationships, get the priamry label, create a Cypher-DSL return expressions
 *
 * @param nodeType the class associated with the definition
 * @param persistentEntity defines Neo4j's view on the node
 * @param oneRelationshipDefinitions list of all one relationship definitions
 * @param manyRelationshipDefinitions list of all many relationship definitions
 */
class NodeDefinition(
    val nodeType: KClass<out Node>,
    oneRelationshipDefinitions: List<OneRelationshipDefinition>,
    manyRelationshipDefinitions: List<ManyRelationshipDefinition>,
    val persistentEntity: Neo4jPersistentEntity<*>
) {
    /**
     * Map of all relationship definitions by GraphQL name
     */
    val relationshipDefinitions =
        (oneRelationshipDefinitions + manyRelationshipDefinitions).associateBy { it.graphQLName }

    /**
     * map of all relationship definitions by defining property
     */
    private val relationshipDefinitionsByProperty =
        (oneRelationshipDefinitions + manyRelationshipDefinitions).associateBy { it.property }

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
     * map of all found authorizations from authorization name to [MergedAuthorization]
     */
    val authorizations: Map<String, MergedAuthorization> = generateAuthorizations()

    /**
     * The primary label of the [Node]
     */
    val primaryLabel: String get() = persistentEntity.primaryLabel

    init {
        val expressions = CypherGenerator.INSTANCE.createReturnStatementForMatch(persistentEntity)
        if (expressions.size != 1) {
            throw IllegalStateException("Cannot get return expression for $nodeType, probably due to cyclic references: $expressions")
        }
        returnExpression = expressions.first()
        returnNodeName = Constants.NAME_OF_TYPED_ROOT_NODE.apply(persistentEntity)
    }

    /**
     * Creates all authorizations by search for [Authorization] annotations on [nodeType] and all super types
     *
     * @return the map of found merged authorizations by authorization name
     */
    private fun generateAuthorizations(): Map<String, MergedAuthorization> {
        val allAuthorizations = nodeType.springFindRepeatableAnnotations<Authorization>()
        return allAuthorizations.groupBy { it.name }
            .mapValues { (name, authorizations) ->
                val authorization = MergedAuthorization(
                    name,
                    authorizations.flatMap { it.allow.toList() }.toSet(),
                    authorizations.flatMap { it.allowFromRelated.toList() }
                        .map { propertyName ->
                            val property = nodeType.memberProperties.first { it.name == propertyName }
                            relationshipDefinitionsByProperty[property]!!
                        }.toSet(),
                    authorizations.flatMap { it.disallow.toList() }.toSet()
                )
                authorization
            }
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
        return relationshipDefinitionsByProperty[property]!!
    }

    override fun toString() = nodeType.getSimpleName()
}
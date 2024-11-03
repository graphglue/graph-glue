package io.github.graphglue.definition

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import io.github.graphglue.connection.generateConnectionFieldDefinition
import io.github.graphglue.connection.model.Connection
import io.github.graphglue.data.LazyLoadingContext
import io.github.graphglue.data.execution.FieldFetchingContext
import io.github.graphglue.data.execution.NodeQueryEntry
import io.github.graphglue.data.execution.NodeQueryParser
import io.github.graphglue.data.execution.NodeQueryResult
import io.github.graphglue.graphql.schema.SchemaTransformationContext
import io.github.graphglue.model.Node
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 *  @param graphQLName the name of the GraphQL field
 *  @param description the description of the GraphQL field
 *  @param properties properties defining the aggregation path
 */
class AggregatedRelationshipFieldDefinition(
    override val graphQLName: String,
    val description: String,
    val properties: List<PropertyWithOwner>,
) : FieldDefinition(null) {

    /**
     * Cached list of [RelationshipDefinition]s mapped from [properties]
     */
    private var cachedRelationshipDefinitions: List<RelationshipDefinition>? = null

    /**
     * Gets the [RelationshipDefinition] that [properties] represent.
     * Caches the result
     *
     * @param nodeDefinitionCollection used to resolve [KClass] to [NodeDefinition]
     * @return the generated [RelationshipDefinition]s
     */
    private fun getRelationshipDefinitions(nodeDefinitionCollection: NodeDefinitionCollection): List<RelationshipDefinition> {
        if (cachedRelationshipDefinitions == null) {
            val relationshipDefinitions = mutableListOf<RelationshipDefinition>()
            for ((property, owner) in properties) {
                val nodeDefinition = nodeDefinitionCollection.getNodeDefinition(owner)
                val relationshipDefinition = nodeDefinition.getRelationshipDefinitionOfProperty(property)
                relationshipDefinitions += relationshipDefinition
            }
            cachedRelationshipDefinitions = relationshipDefinitions
        }
        return cachedRelationshipDefinitions!!
    }

    override fun createGraphQLResult(result: Any?, lazyLoadingContext: LazyLoadingContext): Any {
        return Connection.fromQueryResult(result as NodeQueryResult<*>, lazyLoadingContext.nodeQueryParser.objectMapper)
    }

    override fun createQueryEntry(
        dfe: DataFetchingEnvironment,
        context: FieldFetchingContext,
        nodeQueryParser: NodeQueryParser,
        onlyOnTypes: List<NodeDefinition>?
    ): NodeQueryEntry<*> {
        return nodeQueryParser.generateSubQuery(
            this, getRelationshipDefinitions(nodeQueryParser.nodeDefinitionCollection), context, dfe, onlyOnTypes
        )
    }

    override fun generateFieldDefinition(transformationContext: SchemaTransformationContext): GraphQLFieldDefinition {
        val returnNodeType =
            getRelationshipDefinitions(transformationContext.nodeDefinitionCollection).last().nodeKClass
        return generateConnectionFieldDefinition(returnNodeType, graphQLName, description, transformationContext)
    }

}

/**
 * A property with its owning class
 *
 * @param property the property
 * @param owner the class which owns the property
 */
data class PropertyWithOwner(val property: KProperty1<*, *>, val owner: KClass<out Node>)
package io.github.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import io.github.graphglue.data.execution.*
import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.data.repositories.RelationshipDiff
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.graphql.extensions.getParentNodeDefinition
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.reflect.KProperty1

/**
 * Base class for many and one node properties
 * Provides cache based graphql functionality, and abstract methods with allow persistence
 *
 * @param parent the node which hosts this property
 * @param property the property on the class
 * @param T the type of the value [Node] (s)
 */
abstract class BaseProperty<T : Node?>(protected val parent: Node, protected val property: KProperty1<*, *>) {

    /**
     * Cache for database results for specific query parts
     */
    private val cache = IdentityHashMap<NodeQueryOptions, NodeQueryResult<T>>()

    /**
     * Gets the result of a GraphQL query
     * Uses the cache to obtain the result, and if no cache entry was found, creates a
     * new database query and executes it
     * Uses [constructGraphQLResult] to create the final result
     *
     * @param nodeQueryParser used to obtain the [NodeDefinitionCollection] and [ObjectMapper]
     * @param dataFetchingEnvironment environment to fetch data, used to parse subtree of fetched nodes
     * @return the result, including a new local context
     */
    suspend fun getFromGraphQL(
        @Autowired @GraphQLIgnore
        nodeQueryParser: NodeQueryParser,
        dataFetchingEnvironment: DataFetchingEnvironment
    ): DataFetcherResult<*> {
        val parentNodeQueryPart = dataFetchingEnvironment.getLocalContext<NodeQueryPart>()
        val result: NodeQueryResult<T>
        val localContext = if (parentNodeQueryPart != null) {
            val providedNodeQuery =
                parentNodeQueryPart.getSubQuery(dataFetchingEnvironment.executionStepInfo.resultKey) {
                    dataFetchingEnvironment.getParentNodeDefinition(nodeQueryParser.nodeDefinitionCollection)
                }.query
            val options = providedNodeQuery.options
            result = cache[options] ?: throw IllegalStateException("Result not found in cache")
            providedNodeQuery
        } else {
            val (loadResult, nodeQuery) = parent.loadNodesOfRelationship<T>(property, dataFetchingEnvironment)
            result = loadResult
            nodeQuery
        }
        return constructGraphQLResult(result, localContext, nodeQueryParser)
    }

    /**
     * Called to register a database query result
     * Adds the result to the cache
     * Can be overridden to add custom behavior (super should be called in this case)
     *
     * @param nodeQueryResult the result of the database query
     */
    internal open fun registerQueryResult(nodeQueryResult: NodeQueryResult<T>) {
        cache[nodeQueryResult.options] = nodeQueryResult
    }

    /**
     * Constructs the field result for a graphql query
     *
     * @param result the result of the database query, might be cached or newly queried
     * @param localContext the existing local context, might be transformed
     * @param nodeQueryParser parser for node queries, e.g. can be used to obtain object mapper
     * @return the field result
     */
    protected abstract fun constructGraphQLResult(
        result: NodeQueryResult<T>,
        localContext: NodeQuery?,
        nodeQueryParser: NodeQueryParser
    ): DataFetcherResult<*>

    /**
     * Gets the diff of added and removed relationships to persist in the database
     *
     * @param nodeIdLookup lookup from [Node] to its id to add relation to non-persisted nodes
     *                     (these nodes are already persisted, but not newly fetched from the database)
     * @param nodeDefinition the definition of the nodes in this property, can be used to get the Label(s) of the node(s)
     * @return the diff which describes how to add and remove relationships
     */
    internal abstract fun getRelationshipDiff(
        nodeIdLookup: Map<Node, String>,
        nodeDefinition: NodeDefinition
    ): RelationshipDiff

    /**
     * Gets [Node]s which should be persisted when this [Node] is persisted
     */
    internal abstract fun getRelatedNodesToSave(): Collection<Node>

    /**
     * Marker for lazy loading delegates, necessary for property type based filter & order generation
     *
     * @param R the type of the property
     */
    interface LazyLoadingDelegate<R>
}
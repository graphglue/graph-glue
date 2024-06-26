package io.github.graphglue.model.property

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import io.github.graphglue.data.execution.*
import io.github.graphglue.data.repositories.RelationshipDiff
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.definition.RelationshipDefinition
import io.github.graphglue.graphql.extensions.getParentNodeDefinition
import io.github.graphglue.model.Node
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

/**
 * Base class for many and one node property delegates
 * Provides cache based GraphQL functionality, and abstract methods with allow persistence
 *
 * @param parent the node which hosts this property
 * @param property the property on the class
 * @param T the type of the value [Node] (s)
 * @param R the type of property
 */
abstract class BasePropertyDelegate<T : Node?, R>(
    protected val parent: Node,
    protected val property: KProperty1<*, *>
) {

    /**
     * Cache for database results for specific query parts
     */
    private val cache = IdentityHashMap<NodeQueryOptions, NodeQueryResult<T>>()

    /**
     * NodeCache used to load this property
     * might be set after initial loading, but must not be changed
     */
    protected var nodeCache: NodeCache? = null

    /**
     * Dynamic type of [parent] and name of [property], can be used for error messages without leaking information
     */
    protected val propertyName = "${parent::class.qualifiedName}.${property.name}"

    private val lazyLoadingDelegate = object : LazyLoadingDelegate<T, R> {
        override suspend fun invoke(cache: NodeCache?): R {
            if (nodeCache != null && cache != null && nodeCache != cache) {
                throw IllegalStateException("This property was already loaded with another cache")
            }
            return getLoadedProperty(cache)
        }
    }

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
        @Autowired
        @GraphQLIgnore
        nodeQueryParser: NodeQueryParser,
        dataFetchingEnvironment: DataFetchingEnvironment
    ): DataFetcherResult<*> {
        val parentNodeQueryPart = dataFetchingEnvironment.getLocalContext<NodeQueryPart>()
        val result: NodeQueryResult<T>
        val localContext = if (parentNodeQueryPart != null) {
            val providedNodeQuery =
                parentNodeQueryPart.subQueries.getEntry(dataFetchingEnvironment.executionStepInfo.resultKey) {
                    dataFetchingEnvironment.getParentNodeDefinition(nodeQueryParser.nodeDefinitionCollection)
                }.query
            val options = providedNodeQuery.options
            result = cache[options] ?: NodeQueryResult(options, emptyList(), 0)
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
     * @param nodeDefinition the definition of the nodes in this property, can be used to get the Label(s) of the node(s)
     * @return the diff which describes how to add and remove relationships
     */
    internal abstract fun getRelationshipDiff(
        nodeDefinition: NodeDefinition
    ): RelationshipDiff

    /**
     * Gets [Node]s which should be persisted when this [Node] is persisted
     *
     * @return other nodes to save
     */
    internal abstract fun getRelatedNodesToSave(): Collection<Node>

    /**
     * Gets related nodes defined by this property, but only those already loaded (therefore no lazy loading)
     * The relationships do not have to be persisted yet
     *
     * @return the already loaded related nodes
     */
    internal abstract fun getLoadedRelatedNodes(): Collection<Node>

    /**
     * Gets the loaded property which is returned by the lazy loading delegate
     *
     * @param cache used to load nodes from, if provided, not loading deleted nodes
     * @return the loaded property
     */
    internal abstract suspend fun getLoadedProperty(cache: NodeCache?): R

    /**
     * Gets the lazy loading delegate which is used to get the value of the property
     *
     * @param thisRef the [Node] containing the delegated property
     * @param property the delegated property
     * @return the lazy loading delegate which can be used to get the property
     */
    operator fun getValue(thisRef: Node, property: KProperty<*>): LazyLoadingDelegate<T, R> {
        return lazyLoadingDelegate
    }

    /**
     * Called to validate the property
     * Should throw an exception if the property is invalid, with an appropriate reason
     *
     * @param savingNodes the nodes which are currently saved
     * @param relationshipDefinition definition of the relationship
     * @param nodeDefinitionCollection used to obtain the inverse of [relationshipDefinition]
     */
    abstract fun validate(
        savingNodes: Set<Node>,
        relationshipDefinition: RelationshipDefinition,
        nodeDefinitionCollection: NodeDefinitionCollection
    )
}
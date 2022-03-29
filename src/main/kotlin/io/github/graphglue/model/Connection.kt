package io.github.graphglue.model

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.graphglue.graphql.connection.order.Order
import io.github.graphglue.graphql.extensions.getDataFetcherResult
import io.github.graphglue.neo4j.execution.NodeQueryResult
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment

/**
 * Connection used as ObjectType in the GraphQL API
 * Represents the many side of a relation and supports filtering, ordering and pagination
 *
 * @param nodes all related nodes
 * @param pageInfo general connection information
 * @param totalCount if fetched, the total amount of items in the relation
 * @param order defines how the items are ordered in the connection, necessary for cursor definition
 */
class Connection<T : Node>(
    private val nodes: List<T>,
    val pageInfo: PageInfo,
    private val totalCount: Int?,
    private val order: Order<T>
) {

    /**
     * Returns the nodes and sets the local context necessary to use caching
     *
     * @param dataFetchingEnvironment defines how the query fetches data
     * @return all nodes and the local context necessary for caching
     */
    fun nodes(dataFetchingEnvironment: DataFetchingEnvironment): DataFetcherResult<List<T>> {
        return dataFetchingEnvironment.getDataFetcherResult(nodes, dataFetchingEnvironment.executionStepInfo.resultKey)
    }

    /**
     * Returns the nodes associated with a cursor
     * local context is handled by the returned [Edge] (see [Edge.node]) and therefore not set by this method
     *
     * @return a list of all edges
     */
    fun edges(): List<Edge<T>> {
        return nodes.map { Edge(it, order) }
    }

    /**
     * The total count of nodes in the connection (before filtering and pagination)
     *
     * @return the total count
     * @throws IllegalStateException if totalCount is not available because it was not fetched
     */
    fun totalCount(): Int {
        return totalCount ?: throw IllegalStateException("totalCount not available")
    }

    companion object {

        /**
         * Creates a [Connection] from a [NodeQueryResult]
         * The provided `queryResult` may contain one more [Node] then defined by first or last
         * to calculate hasNextPage and hasPreviousPage. If so, this node is removed from the node list
         *
         * @param T the [Node] type of the returned [Connection]
         * @param queryResult the result of the database query
         * @param objectMapper necessary for cursor generation
         * @return the generated [Connection]
         */
        @Suppress("UNCHECKED_CAST")
        fun <T : Node> fromQueryResult(
            queryResult: NodeQueryResult<T>,
            objectMapper: ObjectMapper
        ): Connection<T> {
            val options = queryResult.options
            val nodes = if (options.first != null) {
                queryResult.nodes.subList(0, (options.first - 1).coerceAtMost(queryResult.nodes.size))
            } else if (options.last != null) {
                queryResult.nodes.subList(
                    (queryResult.nodes.size - (options.last - 1)).coerceAtLeast(0),
                    queryResult.nodes.size
                )
            } else {
                queryResult.nodes
            }
            val pageInfo = PageInfo(options, queryResult.nodes, nodes, objectMapper)
            return Connection(nodes, pageInfo, queryResult.totalCount, options.orderBy as Order<T>)
        }
    }
}
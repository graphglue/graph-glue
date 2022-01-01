package de.graphglue.model

import com.fasterxml.jackson.databind.ObjectMapper
import de.graphglue.graphql.connection.order.Order
import de.graphglue.graphql.extensions.getDataFetcherResult
import de.graphglue.neo4j.execution.NodeQueryResult
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment

class Connection<T : Node>(
    private val nodes: List<T>,
    val pageInfo: PageInfo,
    private val totalCount: Int?,
    private val order: Order<T>
) {

    fun nodes(dataFetchingEnvironment: DataFetchingEnvironment): DataFetcherResult<List<T>> {
        return dataFetchingEnvironment.getDataFetcherResult(nodes, dataFetchingEnvironment.executionStepInfo.resultKey)
    }

    fun edges(): List<Edge<T>> {
        return nodes.map { Edge(it, order) }
    }

    fun totalCount(): Int {
        return totalCount ?: throw IllegalStateException("totalCount not available")
    }

    companion object {
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
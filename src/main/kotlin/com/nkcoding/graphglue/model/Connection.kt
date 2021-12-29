package com.nkcoding.graphglue.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.nkcoding.graphglue.graphql.execution.EDGES_PART_ID
import com.nkcoding.graphglue.graphql.execution.NODES_PART_ID
import com.nkcoding.graphglue.graphql.execution.NodeQuery
import com.nkcoding.graphglue.neo4j.execution.NodeQueryResult
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment

class Connection<T : Node>(private val nodes: List<T>, val pageInfo: PageInfo, private val totalCount: Int?) {

    fun nodes(dataFetchingEnvironment: DataFetchingEnvironment): DataFetcherResult<List<T>> {
        return getDataFetcherResult(dataFetchingEnvironment, nodes, NODES_PART_ID)
    }

    fun edges(dataFetchingEnvironment: DataFetchingEnvironment): DataFetcherResult<List<Edge<T>>> {
        val edges = nodes.map { Edge(it) }
        return getDataFetcherResult(dataFetchingEnvironment, edges, EDGES_PART_ID)
    }

    private fun <R> getDataFetcherResult(
        dataFetchingEnvironment: DataFetchingEnvironment,
        result: R,
        partId: String
    ): DataFetcherResult<R> {
        val nodeQuery = dataFetchingEnvironment.getLocalContext<NodeQuery>()
        return if (nodeQuery != null) {
            DataFetcherResult.newResult<R>()
                .data(result)
                .localContext(nodeQuery.parts[partId])
                .build()
        } else {
            DataFetcherResult.newResult<R>()
                .data(result)
                .build()
        }
    }

    fun totalCount(): Int {
        return totalCount ?: throw IllegalStateException("totalCount not available")
    }

    companion object {
        fun <T: Node> fromQueryResult(
            queryResult: NodeQueryResult<T>,
            objectMapper: ObjectMapper
        ): Connection<T> {
            val options = queryResult.options
            val nodes = if (options.first != null) {
                queryResult.nodes.subList(0, options.first)
            } else if (options.last != null) {
                queryResult.nodes.subList((queryResult.nodes.size - options.last).coerceAtLeast(0), queryResult.nodes.size)
            } else {
                queryResult.nodes
            }
            val pageInfo = PageInfo(options, queryResult.nodes, nodes, objectMapper)
            return Connection(nodes, pageInfo, queryResult.totalCount)
        }
    }
}
package com.nkcoding.graphglue.model

import com.nkcoding.graphglue.graphql.execution.EDGES_PART_ID
import com.nkcoding.graphglue.graphql.execution.NODES_PART_ID
import com.nkcoding.graphglue.graphql.execution.NodeQuery
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
}
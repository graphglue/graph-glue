package com.nkcoding.graphglue.model

class Connection<T : Node>(val nodes: List<T>, val pageInfo: PageInfo, private val totalCount: Int?) {
    fun edges(): List<Edge<T>> {
        return nodes.map { Edge(it) }
    }

    fun totalCount(): Int {
        return totalCount ?: throw IllegalStateException("totalCount not available")
    }
}
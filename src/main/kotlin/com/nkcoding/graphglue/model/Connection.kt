package com.nkcoding.graphglue.model

class Connection<T : Node>(val nodes: List<T>, val pageInfo: PageInfo, val totalCount: Int) {
    fun edges(): List<T> {
        TODO()
    }
}
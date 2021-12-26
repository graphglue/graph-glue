package com.nkcoding.graphglue.graphql.execution

import com.nkcoding.graphglue.model.Node

interface QueryResultContainer {
    fun addQueryResult(nodes: List<Node>, options: QueryOptions)
}
package de.graphglue.graphql.execution

import de.graphglue.model.Node

interface QueryResultContainer {
    fun addQueryResult(nodes: List<Node>, options: QueryOptions)
}
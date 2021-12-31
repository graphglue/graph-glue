package de.graphglue.neo4j.execution

import de.graphglue.graphql.execution.QueryOptions
import de.graphglue.model.Node

data class NodeQueryResult<T : Node?>(val options: QueryOptions, val nodes: List<T>, val totalCount: Int?)

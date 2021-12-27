package com.nkcoding.graphglue.neo4j.execution

import com.nkcoding.graphglue.graphql.execution.QueryOptions
import com.nkcoding.graphglue.model.Node

data class NodeQueryResult<T: Node?>(val options: QueryOptions, val nodes: List<T>, val totalCount: Int?)

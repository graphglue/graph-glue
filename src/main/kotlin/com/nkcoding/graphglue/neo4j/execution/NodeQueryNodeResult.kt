package com.nkcoding.graphglue.neo4j.execution

import com.nkcoding.graphglue.model.Node

data class NodeQueryNodeResult(val node: Node, val subQueries: List<NodeSubQueryResult>)
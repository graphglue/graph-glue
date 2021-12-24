package com.nkcoding.graphglue.neo4j.execution

import com.nkcoding.graphglue.graphql.execution.NodeQuery

data class NodeQueryResult(val nodeQuery: NodeQuery, val nodes: List<NodeQueryNodeResult>)
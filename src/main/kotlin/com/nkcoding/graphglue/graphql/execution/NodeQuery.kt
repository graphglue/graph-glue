package com.nkcoding.graphglue.graphql.execution

import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinition

class NodeQuery(
    val definition: NodeDefinition,
    val options: QueryOptions,
    val parts: Map<String, NodeQueryPart>
)
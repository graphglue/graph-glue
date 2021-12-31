package de.graphglue.graphql.execution

import de.graphglue.graphql.execution.definition.NodeDefinition

class NodeQuery(
    val definition: NodeDefinition,
    val options: QueryOptions,
    val parts: Map<String, NodeQueryPart>
)
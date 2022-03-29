package io.github.graphglue.db.execution

import io.github.graphglue.model.Node
import io.github.graphglue.db.execution.definition.NodeDefinition

/**
 * Defines a query which fetches Nodes of type `definition`
 *
 * @param definition defines which type of [Node] is fetched
 * @param options options for the query, e.g. pagination
 * @param parts subqueries partitioned into parts
 */
class NodeQuery(
    val definition: NodeDefinition,
    val options: NodeQueryOptions,
    val parts: Map<String, NodeQueryPart>
)
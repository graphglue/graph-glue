package io.github.graphglue.data.execution

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.model.Node

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
) {

    /**
     * The cost of this query
     */
    val cost: Int = parts.values.sumOf { it.cost } + 1

    /**
     * Checks if any part of this node query affects the given node
     *
     * @param node the node to check
     * @return true if any part affects the node
     */
    fun affectsNode(node: Node): Boolean {
        return parts.values.any { it.affectsNode(node) }
    }
}
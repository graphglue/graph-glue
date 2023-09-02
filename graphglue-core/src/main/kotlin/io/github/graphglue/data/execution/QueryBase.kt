package io.github.graphglue.data.execution

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.model.Node

/**
 * Base class for queries
 *
 * @param definition defines which type of [Node] is fetched
 * @param parts subqueries partitioned into parts
 * @param T the type of the query
 */
abstract class QueryBase<T : QueryBase<T>>(
    val definition: NodeDefinition,
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

    /**
     * Copies this query and sets the parts to the given parts
     */
    abstract fun copyWithParts(parts: Map<String, NodeQueryPart>): T

}
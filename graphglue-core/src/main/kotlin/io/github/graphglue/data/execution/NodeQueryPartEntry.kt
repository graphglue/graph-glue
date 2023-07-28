package io.github.graphglue.data.execution

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.model.Node

/**
 * Subclass for [NodeExtensionField] and [NodeSubQuery]
 */
abstract class NodeQueryPartEntry(
    val onlyOnTypes: List<NodeDefinition>,
    val resultKey: String
) {

    /**
     * The cost of this entry
     */
    abstract val cost: Int

    /**
     * Checks if this entry affects the given node
     *
     * @param node the node to check
     * @return true if this entry affects the node
     */
    fun affectsNode(node: Node): Boolean {
        return onlyOnTypes.any { it.nodeType.isInstance(node) }
    }
}
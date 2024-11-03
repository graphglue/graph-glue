package io.github.graphglue.data.execution

import io.github.graphglue.definition.FieldDefinition
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.model.Node

/**
 * Subclass for [NodeExtensionField] and [NodeSubQuery]
 *
 * @param onlyOnTypes the list of node types this entry should be fetched for
 * @param resultKeyPath the path to the key which fetches this field
 * @param fieldDefinition definition of the queried field
 * @param T the type of the field definition
 */
abstract class NodeQueryEntry<T : FieldDefinition>(
    val onlyOnTypes: List<NodeDefinition>?,
    val resultKeyPath: String,
    val fieldDefinition: T
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
        if (onlyOnTypes == null) {
            return true
        }
        return onlyOnTypes.any { it.nodeType.isInstance(node) }
    }
}
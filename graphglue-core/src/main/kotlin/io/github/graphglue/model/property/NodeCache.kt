package io.github.graphglue.model.property

import io.github.graphglue.model.Node

/**
 * A cache which can be used that lazy loading uses existing nodes instead of new ones
 *
 * @param nodes already fetched nodes
 */
class NodeCache(nodes: Collection<Node> = emptySet()) {

    /**
     * Internal representation of the loaded [Node]s
     */
    private val internalNodes = nodes.associateBy { it }.toMutableMap()

    /**
     * The loaded [Node]s, excluding deleted ones
     */
    val nodes: Set<Node> get() = internalNodes.keys

    /**
     * Adds a node to the cache
     *
     * @param node the [Node] to add
     */
    fun add(node: Node) {
        internalNodes[node] = node
    }

    /**
     * Gets the cached node equivalent to [node], otherwise puts
     * it into the cache
     * If already marked as deleted, or provided `null`, return `null`
     *
     * @param T the type of node
     * @param node the node to get
     * @return the node from the cache or [node]
     */
    @Suppress("UNCHECKED_CAST")
    fun <T: Node?> getOrAdd(node: T): T? {
        return when (node) {
            null -> null
            else -> {
                internalNodes.computeIfAbsent(node) {
                    node
                } as T
            }
        }
    }
}
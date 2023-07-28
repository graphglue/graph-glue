package io.github.graphglue.data.execution

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.model.Node

/**
 * Part of a [NodeQuery], consisting of a list of [NodeSubQuery]s
 * Used to partition the total list of subqueries
 *
 * @param subQueries the list of [NodeSubQuery]s
 * @param extensionFields the list of [NodeExtensionField]s
 */
class NodeQueryPart(subQueries: List<NodeSubQuery>, extensionFields: List<NodeExtensionField>) {
    /**
     * Lookup for subqueries
     */
    val subQueries = NodeQueryPartEntrySet(subQueries)

    /**
     * Lookup for extension fields
     */
    val extensionFields = NodeQueryPartEntrySet(extensionFields)

    /**
     * The cost of this NodeQueryPart
     */
    val cost: Int = subQueries.sumOf { it.cost } + extensionFields.sumOf { it.cost }

    fun affectsNode(node: Node): Boolean {
        return subQueries.affectsNode(node) || extensionFields.affectsNode(node)
    }
}

/**
 * Set of NodeQueryPartEntries
 * @param T the type of entries handled
 */
class NodeQueryPartEntrySet<T : NodeQueryPartEntry>(val entries: List<T>) {
    /**
     * Lookup to get an entry efficiently by `resultKey`
     * Each group contains all entries under a specific resultKey
     * Necessary as `resultKey` may not be unique, as only
     * [NodeQueryPartEntry.resultKey] with any element of [NodeQueryPartEntry.onlyOnTypes] must be unique
     */
    private val lookup = entries.groupBy { it.resultKey }

    /**
     * Gets an entry by [NodeQueryPartEntry.resultKey].
     * As multiple subqueries can use the same `resultKey` if they use different [NodeQueryPartEntry.onlyOnTypes],
     * a list of [NodeDefinition]s may be necessary to get the correct subquery.
     * This is provided as provider as evaluation is expensive and only necessary in few cases
     *
     * @param resultKey the key of the subquery
     * @param nodeDefinitionProvider provides the set of [NodeDefinition]s for which the subquery must be fetched
     * @return the found subquery
     */
    fun getEntry(resultKey: String, nodeDefinitionProvider: () -> NodeDefinition): T {
        val entries = lookup[resultKey]!!
        return if (entries.size == 1) {
            entries.first()
        } else {
            val nodeDefinition = nodeDefinitionProvider()
            entries.first { it.onlyOnTypes.contains(nodeDefinition) }
        }
    }

    /**
     * Checks if any entry affects the given node
     *
     * @param node the node to check
     * @return true if any entry affects the node
     */
    fun affectsNode(node: Node): Boolean {
        return entries.any { it.affectsNode(node) }
    }
}
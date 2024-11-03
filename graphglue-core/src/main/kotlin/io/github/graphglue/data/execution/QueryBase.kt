package io.github.graphglue.data.execution

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.model.Node

/**
 * Base class for queries
 *
 * @param definition defines which type of [Node] is fetched
 * @param entries parts of the query to execute with this query
 * @param T the type of the query
 */
abstract class QueryBase<T : QueryBase<T>>(
    val definition: NodeDefinition,
    val entries: List<NodeQueryEntry<*>>
) {

    /**
     * Lookup to get an entry efficiently by `resultKeyPath`
     * Each group contains all entries under a specific resultKey
     * Necessary as `resultKey` may not be unique, as only
     * [NodeQueryEntry.resultKeyPath] with any element of [NodeQueryEntry.onlyOnTypes] must be unique
     */
    private val entryLookup = entries.groupBy { it.resultKeyPath }

    /**
     * The cost of this NodeQueryPart
     */
    val cost: Int = entries.sumOf { it.cost } + 1

    /**
     * Gets an entry by [NodeQueryEntry.resultKeyPath].
     * As multiple subqueries can use the same `resultKeyPath` if they use different [NodeQueryEntry.onlyOnTypes],
     * a list of [NodeDefinition]s may be necessary to get the correct subquery.
     * This is provided as provider as evaluation is expensive and only necessary in few cases
     *
     * @param resultKey the key of the subquery
     * @param nodeDefinitionProvider provides the set of [NodeDefinition]s for which the subquery must be fetched
     * @return the found subquery
     */
    fun getEntry(resultKey: String, nodeDefinitionProvider: () -> NodeDefinition): NodeQueryEntry<*> {
        val entries = entryLookup[resultKey]!!
        return if (entries.size == 1) {
            entries.first()
        } else {
            val nodeDefinition = nodeDefinitionProvider()
            entries.first { it.onlyOnTypes == null || it.onlyOnTypes.contains(nodeDefinition) }
        }
    }

    /**
     * Checks if any part of this node query affects the given node
     *
     * @param node the node to check
     * @return true if any part affects the node
     */
    fun affectsNode(node: Node): Boolean {
        return entries.any { it.affectsNode(node) }
    }

    /**
     * Copies this query and sets the entries to the given entries
     */
    abstract fun copyWithEntries(entries: List<NodeQueryEntry<*>>): T

}
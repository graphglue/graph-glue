package io.github.graphglue.neo4j.execution

import de.graphglue.neo4j.execution.definition.NodeDefinition

/**
 * Part of a [NodeQuery], consisting of a list of [NodeSubQuery]s
 * Used to partition the total list of subqueries
 *
 * @param subQueries the list of [NodeSubQuery]s
 */
class NodeQueryPart(val subQueries: List<NodeSubQuery>) {
    /**
     * Lookup to get a subquery efficiently by `resultKey`
     * Each group contains all subqueries under a specific resultKey
     * Necessary as `resultKey` may not be unique, as only
     * [NodeSubQuery.resultKey] with any element of [NodeSubQuery.onlyOnTypes] must be unique
     */
    private val subQueryLookup = subQueries.groupBy { it.resultKey }

    /**
     * Gets a subquery by [NodeSubQuery.resultKey].
     * As multiple subqueries can use the same `resultKey` if they use different [NodeSubQuery.onlyOnTypes],
     * a list of [NodeDefinition]s may be necessary to get the correct subquery.
     * This is provided as provider as evaluation is expensive and only necessary in few cases
     *
     * @param resultKey the key of the subquery
     * @param nodeDefinitionProvider provides the set of [NodeDefinition]s for which the subquery must be fetched
     * @return the found subquery
     */
    fun getSubQuery(resultKey: String, nodeDefinitionProvider: () -> NodeDefinition): NodeSubQuery {
        val subQueries = subQueryLookup[resultKey]!!
        return if (subQueries.size == 1) {
            subQueries.first()
        } else {
            val nodeDefinition = nodeDefinitionProvider()
            subQueries.first { it.onlyOnTypes.contains(nodeDefinition) }
        }
    }
}
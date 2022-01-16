package de.graphglue.neo4j.execution

import de.graphglue.graphql.connection.order.IdOrder
import de.graphglue.graphql.connection.order.Order
import de.graphglue.neo4j.CypherConditionGenerator

/**
 * Defines how a [NodeQuery] fetches data
 *
 * @property filters filters which are applied to filter out matched nodes
 * @property orderBy the order in which nodes are ordered
 * @property after if present, only nodes after this parsed cursor are fetched
 * @property before if present, only properties before this parsed cursor are fetched
 * @property first if present, only the first n nodes are fetched
 * @property last if present, only the last n nodes are fetched
 * @property fetchTotalCount totalCount is only fetched if `true`
 */
data class QueryOptions(
    val filters: List<CypherConditionGenerator> = emptyList(),
    val orderBy: Order<*> = IdOrder,
    val after: Map<String, Any?>? = null,
    val before: Map<String, Any?>? = null,
    val first: Int? = null,
    val last: Int? = null,
    val fetchTotalCount: Boolean = true
) {
    init {
        if ((first != null) && (last != null)) {
            throw IllegalArgumentException("first and last can't both be present")
        }
        if (first != null && first < 0) {
            throw IllegalArgumentException("first must be >= 0")
        }
        if (last != null && last < 0) {
            throw IllegalArgumentException("last must be >= 0")
        }
    }

    /**
     * Can be used to check if a node fetches all nodes
     */
    val isAllQuery get() = (filters.isEmpty()) && (after == null) && (before == null) && (first == null) && (last == null)
}

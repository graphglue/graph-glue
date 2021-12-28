package com.nkcoding.graphglue.graphql.execution

import com.nkcoding.graphglue.graphql.connection.order.IdOrder
import com.nkcoding.graphglue.graphql.connection.order.Order
import com.nkcoding.graphglue.neo4j.CypherConditionGenerator

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

    val isAllQuery get() = (filters.isEmpty()) && (after == null) && (before == null) && (first == null) && (last == null)
}

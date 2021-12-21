package com.nkcoding.graphglue.graphql.execution

import com.nkcoding.graphglue.graphql.connection.filter.model.Filter
import com.nkcoding.graphglue.graphql.connection.order.Order

data class QueryOptions(
    val filter: Filter? = null,
    val orderBy: Order<*>? = null,
    val after: Map<String, Any?>? = null,
    val before: Map<String, Any?>? = null,
    val first: Int? = null,
    val last: Int? = null
)  {
    init {
        if (orderBy == null && (after != null || before != null || first != null || last != null)) {
            throw IllegalStateException("If after, before, first or last is != null, orderBy must be provided")
        }
    }
}

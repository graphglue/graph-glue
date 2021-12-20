package com.nkcoding.graphglue.graphql.execution

import com.nkcoding.graphglue.graphql.connection.filter.model.Filter
import com.nkcoding.graphglue.graphql.connection.order.Order

data class QueryOptions(
    val filter: Filter? = null,
    val orderBy: Order<*>? = null,
    val after: String? = null,
    val before: String? = null,
    val first: Int? = null,
    val last: Int? = null
)

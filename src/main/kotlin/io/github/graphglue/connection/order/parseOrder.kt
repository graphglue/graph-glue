package io.github.graphglue.connection.order

fun parseOrder(value: Any): Order<*> {
    value as Map<*, *>
    val directionName = value["direction"] as String?
    val direction = if (directionName != null) {
        OrderDirection.valueOf(directionName)
    } else {
        OrderDirection.ASC
    }
    return Order(direction, value["field"] as OrderField<*>? ?: IdOrderField)
}
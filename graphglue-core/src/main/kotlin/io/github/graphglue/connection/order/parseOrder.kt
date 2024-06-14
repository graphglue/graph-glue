package io.github.graphglue.connection.order

/**
 * Parses the GraphQL input for an [Order]
 *
 * @param value the input value from the GraphQL API
 * @return the parsed [Order]
 */
fun parseOrder(value: Any): Order<*> {
    val fields = (value as List<*>).map {
        it as Map<*, *>
        val directionName = it["direction"] as String?
        val direction = if (directionName != null) {
            OrderDirection.valueOf(directionName)
        } else {
            OrderDirection.ASC
        }
        OrderField(it["field"] as OrderPart<*>? ?: IdOrderPart, direction)
    }
    val resultingFields = mutableListOf<OrderField<*>>()
    val foundParts = mutableSetOf<OrderPart<*>>()
    for (field in fields) {
        if (foundParts.contains(field.part)) {
            continue
        }
        resultingFields += field
        foundParts += field.part
    }
    if (IdOrderPart !in foundParts) {
        resultingFields += IdOrderField
    }
    return Order(resultingFields.reversed())
}
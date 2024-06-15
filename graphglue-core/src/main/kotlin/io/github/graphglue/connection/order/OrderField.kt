package io.github.graphglue.connection.order

import io.github.graphglue.model.Node

/**
 * Defines an order without direction
 *
 * @param part the [OrderPart] which defines an order
 * @param direction defines the direction of the order, e.g. ascending or descending
 */
data class OrderField<in T : Node>(val part: OrderPart<T>, val direction: OrderDirection)
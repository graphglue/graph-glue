package io.github.graphglue.connection.order

import io.github.graphglue.model.Node

/**
 * Defines an order without direction
 *
 * @param name the name of the order option
 * @param parts the [OrderPart] which define an order, this set of fields should be unique
 */
class OrderField<in T : Node>(val name: String, val parts: List<OrderPart<T>>) {
    override fun toString() = name
}
package io.github.graphglue.graphql.connection.order

import io.github.graphglue.model.Node

class OrderField<in T : Node>(val name: String, val parts: List<OrderPart<T>>) {
    override fun toString() = name
}
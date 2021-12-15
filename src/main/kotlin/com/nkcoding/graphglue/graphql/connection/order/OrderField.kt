package com.nkcoding.graphglue.graphql.connection.order

import com.nkcoding.graphglue.model.Node

class OrderField<in T: Node>(val name: String, val parts: List<OrderPart<T>>) {
    override fun toString() = name
}
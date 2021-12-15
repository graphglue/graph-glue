package com.nkcoding.testing.model

import com.nkcoding.graphglue.graphql.connection.filter.FilterProperty
import com.nkcoding.graphglue.graphql.connection.order.OrderProperty
import com.nkcoding.graphglue.model.Node

abstract class Leaf(id: String) : Node(id) {
    @FilterProperty
    @OrderProperty
    val text = "I am a leaf"
}


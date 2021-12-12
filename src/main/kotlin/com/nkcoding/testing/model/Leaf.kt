package com.nkcoding.testing.model

import com.nkcoding.graphglue.graphql.connection.filter.FilterProperty
import com.nkcoding.graphglue.model.Node

abstract class Leaf : Node() {
    @FilterProperty
    val text = "I am a leaf"
}


package com.nkcoding.graphglue.graphql.connection.order

import com.nkcoding.graphglue.model.Node
import kotlin.reflect.full.memberProperties

val IdOrderPart = SimpleOrderPart<Node>(Node::class.memberProperties.first { it.name == "id" })

val IdOrderField = OrderField("id", listOf(IdOrderPart))
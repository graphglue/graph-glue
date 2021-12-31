package de.graphglue.graphql.connection.order

import de.graphglue.model.Node
import kotlin.reflect.full.memberProperties

val IdOrderPart = SimpleOrderPart<Node>(Node::class.memberProperties.first { it.name == "id" }, "id")

val IdOrderField = OrderField("id", listOf(IdOrderPart))

val IdOrder = Order(OrderDirection.ASC, IdOrderField)
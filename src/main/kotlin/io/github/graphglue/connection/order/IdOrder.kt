package io.github.graphglue.connection.order

import io.github.graphglue.model.Node
import kotlin.reflect.full.memberProperties

val IdOrderPart = SimpleOrderPart<Node>(Node::class.memberProperties.first { it.name == "id" }, "id")

/**
 * Order field with only [IdOrderPart] as parts
 */
val IdOrderField = OrderField("id", listOf(IdOrderPart))

/**
 * Default order, ascending and only based on the id
 */
val IdOrder = Order(OrderDirection.ASC, IdOrderField)
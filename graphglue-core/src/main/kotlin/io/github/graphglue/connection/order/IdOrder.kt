package io.github.graphglue.connection.order

import io.github.graphglue.model.Node

/**
 * Order part for the id of a [Node]
 */
val IdOrderPart = PropertyOrderPart<Node>(Node::id, "id")

/**
 * Order field with only [IdOrderPart] as parts
 */
val IdOrderField = OrderField(IdOrderPart, OrderDirection.ASC)

/**
 * Default order, ascending and only based on the id
 */
val IdOrder = Order(listOf(IdOrderField))
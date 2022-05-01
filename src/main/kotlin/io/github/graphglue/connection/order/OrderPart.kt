package io.github.graphglue.connection.order

import io.github.graphglue.model.Node

/**
 * Part of a complete order
 * Defines an order over all nodes by ordering based on a property on the database node
 *
 * @param T the type of [Node] for which the order is defined
 * @param name name of the field in the cursor JSON
 * @param neo4jPropertyName name of the property on the database node
 * @param isNullable if true, this part can be null
 */
abstract class OrderPart<in T : Node>(val name: String, val neo4jPropertyName: String, val isNullable: Boolean) {
    /**
     * Gets the value of the field of a [Node]
     *
     * @param node the instance of the [Node] to get the value from
     * @return the value of the node used in cursor generation
     */
    abstract fun getValue(node: T): Any?
}
package io.github.graphglue.connection.order

import io.github.graphglue.model.Node
import org.neo4j.cypherdsl.core.Expression
import org.neo4j.cypherdsl.core.SymbolicName

/**
 * Part of a complete order
 * Defines an order over all nodes by ordering based on a property on the database node
 *
 * @param T the type of [Node] for which the order is defined
 * @param name name of the field in the cursor JSON
 * @param isNullable if true, this part can be null
 * @param isComplex if true, this order part is ignored when generating relationship-based order parts
 */
abstract class OrderPart<in T : Node>(val name: String, val isNullable: Boolean, val isComplex: Boolean) {

    /**
     * Gets the expression for the property on the database node
     *
     * @param node the [SymbolicName] of the node in the query
     * @return the expression for the property on the database node
     */
    abstract fun getExpression(node: SymbolicName): Expression
}
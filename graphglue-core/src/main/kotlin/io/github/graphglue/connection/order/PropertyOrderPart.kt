package io.github.graphglue.connection.order

import io.github.graphglue.model.Node
import org.neo4j.cypherdsl.core.Expression
import org.neo4j.cypherdsl.core.SymbolicName
import kotlin.reflect.KProperty

/**
 * [OrderPart] defined by a [KProperty]
 *
 * @param property defines the part, provides a name and used for [getValue]
 * @param neo4jPropertyName name of the property on the database node
 */
class PropertyOrderPart<T : Node>(
    private val property: KProperty<*>, private val neo4jPropertyName: String
) : OrderPart<T>(property.name, property.returnType.isMarkedNullable) {
    override fun getExpression(node: SymbolicName): Expression {
        return node.property(neo4jPropertyName)
    }
}
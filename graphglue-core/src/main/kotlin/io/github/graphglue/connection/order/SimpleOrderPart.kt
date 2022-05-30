package io.github.graphglue.connection.order

import io.github.graphglue.model.Node
import kotlin.reflect.KProperty

/**
 * [OrderPart] defined by a [KProperty]
 *
 * @param property defines the part, provides a name and used for [getValue]
 * @param neo4jPropertyName name of the property on the database node
 */
class SimpleOrderPart<T : Node>(
    private val property: KProperty<*>, neo4jPropertyName: String
) : OrderPart<T>(property.name, neo4jPropertyName, property.returnType.isMarkedNullable) {
    override fun getValue(node: T): Any? {
        return property.getter.call(node)
    }
}
package com.nkcoding.graphglue.graphql.connection.order

import com.nkcoding.graphglue.model.Node
import kotlin.reflect.KProperty

class SimpleOrderPart<T : Node>(property: KProperty<*>, neo4jPropertyName: String) :
    OrderPart<T>(property, neo4jPropertyName) {
    override fun getValue(node: T): Any? {
        return property.getter.call(node)
    }
}
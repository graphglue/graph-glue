package com.nkcoding.graphglue.graphql.connection.order

import com.nkcoding.graphglue.model.Node
import kotlin.reflect.KProperty

class SimpleOrderPart<T: Node>(property: KProperty<*>) : OrderPart<T>(property) {
    override fun getValue(node: T): Any? {
        return property.getter.call(node)
    }
}
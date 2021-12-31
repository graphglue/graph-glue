package de.graphglue.model

import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

class NodeSetProperty<T : Node>(
    value: Collection<T>? = null,
    parent: Node,
    property: KProperty1<*, *>
) {
    private var nodeSet: NodeSet<T> = NodeSet(value, parent, property)

    operator fun getValue(thisRef: Node, property: KProperty<*>): NodeSet<T> = nodeSet
}
package com.nkcoding.graphglue.model

import kotlin.reflect.KProperty

class NodeSetProperty<T : Node>(value: Collection<T>? = null) {
    private val nodeSet = NodeSet(value)

    operator fun getValue(thisRef: Node, property: KProperty<*>): NodeSet<T> = nodeSet
}
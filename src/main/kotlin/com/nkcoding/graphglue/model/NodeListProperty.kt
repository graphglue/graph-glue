package com.nkcoding.graphglue.model

import kotlin.reflect.KProperty

class NodeListProperty<T: Node> {
    private val nodeList = NodeList<T>()

    operator fun getValue(thisRef: Node, property: KProperty<*>) = nodeList
}
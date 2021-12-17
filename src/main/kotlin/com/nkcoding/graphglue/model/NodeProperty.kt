package com.nkcoding.graphglue.model

import kotlin.reflect.KProperty

class NodeProperty<T: Node> {
    operator fun getValue(thisRef: Node, property: KProperty<*>): T {
        TODO()
    }

    operator fun setValue(thisRef: Node, property: KProperty<*>, value: T) {
        TODO()
    }
}
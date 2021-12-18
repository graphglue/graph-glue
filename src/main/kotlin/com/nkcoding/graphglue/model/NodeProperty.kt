package com.nkcoding.graphglue.model

import com.nkcoding.graphglue.graphql.redirect.RedirectPropertyDelegateClass
import com.nkcoding.graphglue.graphql.redirect.RedirectPropertyFunction
import kotlin.reflect.KProperty

@RedirectPropertyDelegateClass
class NodeProperty<T: Node> {
    operator fun getValue(thisRef: Node, property: KProperty<*>): T {
        TODO()
    }

    operator fun setValue(thisRef: Node, property: KProperty<*>, value: T) {
        TODO()
    }

    @RedirectPropertyFunction
    fun getFromGraphQL(): T {
        TODO()
    }
}
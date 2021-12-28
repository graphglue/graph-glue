package com.nkcoding.graphglue.graphql.extensions

import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.isAccessible

@Suppress("UNCHECKED_CAST")
fun <T> KProperty1<*, *>.getDelegateAccessible(source: Any): T {
    this as KProperty1<Any, *>
    this.isAccessible = true
    return this.getDelegate(source) as T
}
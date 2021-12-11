package com.nkcoding.graphglue.graphql.filter

import com.nkcoding.graphglue.model.Node
import kotlin.reflect.KClass

class FilterDefinitionCollection(val backingCollection: Map<KClass<out Node>, FilterDefinition<out Node>>) {
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Node> getFilterDefinition(): FilterDefinition<T> {
        return backingCollection[T::class] as FilterDefinition<T>
    }
}
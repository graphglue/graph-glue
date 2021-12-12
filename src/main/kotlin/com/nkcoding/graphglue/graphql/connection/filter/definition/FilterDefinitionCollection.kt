package com.nkcoding.graphglue.graphql.connection.filter.definition

import com.nkcoding.graphglue.model.Node
import kotlin.reflect.KClass

class FilterDefinitionCollection(val backingCollection: Map<KClass<out Node>, FilterDefinition<out Node>>) {
    inline fun <reified T : Node> getFilterDefinition(): FilterDefinition<T> {
        @Suppress("UNCHECKED_CAST")
        return backingCollection[T::class] as FilterDefinition<T>
    }
}
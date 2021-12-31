package de.graphglue.graphql.connection.filter.definition

import de.graphglue.model.Node
import kotlin.reflect.KClass

class FilterDefinitionCollection(private val backingCollection: Map<KClass<out Node>, FilterDefinition<out Node>>) {
    inline fun <reified T : Node> getFilterDefinition(): FilterDefinition<T> {
        return getFilterDefinition(T::class)
    }

    fun <T : Node> getFilterDefinition(nodeType: KClass<out Node>): FilterDefinition<T> {
        @Suppress("UNCHECKED_CAST")
        return backingCollection[nodeType] as FilterDefinition<T>
    }
}
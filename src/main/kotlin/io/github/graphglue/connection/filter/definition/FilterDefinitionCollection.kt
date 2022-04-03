package io.github.graphglue.connection.filter.definition

import io.github.graphglue.model.Node
import kotlin.reflect.KClass

/**
 * Set of [FilterDefinition] associated by [Node] type
 * Can be used to get a [FilterDefinition] for a specific [Node] type
 *
 * @param backingCollection map which stores all the [FilterDefinition]s, changes to this map are reflected by this
 */
class FilterDefinitionCollection(private val backingCollection: Map<KClass<out Node>, FilterDefinition<out Node>>) {

    /**
     * Gets a [FilterDefinition] by [Node] type
     *
     * @param T the type of node to get the [FilterDefinition] for
     * @return the found [FilterDefinition]
     */
    inline fun <reified T : Node> getFilterDefinition(): FilterDefinition<T> {
        return getFilterDefinition(T::class)
    }

    /**
     * Gets a [FilterDefinition] by [Node] type
     *
     * @param nodeType the type of node to get the [FilterDefinition] for
     * @return the found [FilterDefinition]
     */
    fun <T : Node> getFilterDefinition(nodeType: KClass<out Node>): FilterDefinition<T> {
        @Suppress("UNCHECKED_CAST")
        return backingCollection[nodeType] as FilterDefinition<T>
    }
}
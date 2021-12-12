package com.nkcoding.graphglue.graphql.connection.filter.definition

import com.nkcoding.graphglue.graphql.connection.filter.TypeFilterDefinitionEntry
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

class SubFilterGenerator(
    private val filters: List<TypeFilterDefinitionEntry>,
    val filterDefinitionCache: FilterDefinitionCache
) {
    /**
     * Generates a filter for a specified type with a specified name
     */
    fun filterForType(type: KClass<*>, name: String): FilterEntryDefinition {
        for (filter in filters) {
            if (type.isSubclassOf(filter.associatedType)) {
                return filter.filterDefinitionFactory(name, this)
            }
        }
        throw IllegalStateException("Cannot create filter for type $type")
    }
}
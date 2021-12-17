package com.nkcoding.graphglue.graphql.connection.filter.definition

import com.nkcoding.graphglue.graphql.connection.filter.TypeFilterDefinitionEntry
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSubtypeOf

class SubFilterGenerator(
    private val filters: List<TypeFilterDefinitionEntry>,
    val filterDefinitionCache: FilterDefinitionCache
) {
    /**
     * Generates a filter for a specified type with a specified name
     */
    fun filterForType(type: KType, name: String): FilterEntryDefinition {
        for (filter in filters) {
            if (type.isSubtypeOf(filter.associatedType)) {
                return filter.filterDefinitionFactory(name, type, this)
            }
        }
        throw IllegalStateException("Cannot create filter for type $type")
    }
}
package com.nkcoding.graphglue.graphql.connection.filter.definition

import com.nkcoding.graphglue.graphql.connection.filter.TypeFilterDefinitionEntry
import com.nkcoding.graphglue.graphql.execution.definition.MutableNodeDefinitionCollection
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinition
import com.nkcoding.graphglue.graphql.extensions.getPropertyName
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.jvmErasure

class SubFilterGenerator(
    private val filters: List<TypeFilterDefinitionEntry>,
    val filterDefinitionCache: FilterDefinitionCache,
    val nodeDefinitionCollection: MutableNodeDefinitionCollection
) {
    /**
     * Generates a filter for a specified type with a specified name
     */
    fun filterForProperty(property: KProperty1<*, *>, parentNodeDefinition: NodeDefinition): FilterEntryDefinition {
        val type = property.returnType
        for (filter in filters) {
            if (type.isSubtypeOf(filter.associatedType)) {
                return filter.filterDefinitionFactory(
                    property.getPropertyName(type.jvmErasure),
                    property,
                    parentNodeDefinition,
                    this
                )
            }
        }
        throw IllegalStateException("Cannot create filter for type $type")
    }
}
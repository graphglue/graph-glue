package io.github.graphglue.graphql.connection.filter.definition

import de.graphglue.graphql.connection.filter.TypeFilterDefinitionEntry
import de.graphglue.graphql.extensions.getPropertyName
import de.graphglue.neo4j.execution.definition.NodeDefinition
import de.graphglue.neo4j.execution.definition.NodeDefinitionCache
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.jvmErasure

class SubFilterGenerator(
    private val filters: List<TypeFilterDefinitionEntry>,
    val filterDefinitionCache: FilterDefinitionCache,
    val nodeDefinitionCollection: NodeDefinitionCache,
    val additionalFilterBeans: Map<String, FilterEntryDefinition>
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
package io.github.graphglue.connection.filter

import io.github.graphglue.connection.filter.definition.FilterEntryDefinition
import io.github.graphglue.connection.filter.definition.SubFilterGenerator
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.model.FilterProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KType

/**
 * Wrapper which associates a type with a FilterDefinitionEntry
 * Used to define filters for a specific type, so that properties of that type can be annotated with
 * [FilterProperty]
 *
 * @param associatedType the supported type of the property
 * @param filterDefinitionFactory function to convert the property into a filter, if null is returned, no filter is added
 */
data class TypeFilterDefinitionEntry(
    val associatedType: KType, val filterDefinitionFactory: (
        name: String, property: KProperty1<*, *>, parentNodeDefinition: NodeDefinition, subFilterGenerator: SubFilterGenerator
    ) -> FilterEntryDefinition?
)
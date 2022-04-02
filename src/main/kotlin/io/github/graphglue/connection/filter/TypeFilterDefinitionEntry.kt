package io.github.graphglue.connection.filter

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.connection.filter.definition.FilterEntryDefinition
import io.github.graphglue.connection.filter.definition.SubFilterGenerator
import kotlin.reflect.KProperty1
import kotlin.reflect.KType

/**
 * Wrapper which associates a type with a FilterDefinitionEntry
 */
data class TypeFilterDefinitionEntry(
    val associatedType: KType,
    val filterDefinitionFactory: (name: String, property: KProperty1<*, *>, parentNodeDefinition: NodeDefinition, subFilterGenerator: SubFilterGenerator) -> FilterEntryDefinition
)
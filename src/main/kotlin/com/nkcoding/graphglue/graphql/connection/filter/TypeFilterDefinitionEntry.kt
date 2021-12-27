package com.nkcoding.graphglue.graphql.connection.filter

import com.nkcoding.graphglue.graphql.connection.filter.definition.FilterEntryDefinition
import com.nkcoding.graphglue.graphql.connection.filter.definition.SubFilterGenerator
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinition
import kotlin.reflect.KProperty1
import kotlin.reflect.KType

/**
 * Wrapper which associates a type with a FilterDefinitionEntry
 */
data class TypeFilterDefinitionEntry(
    val associatedType: KType,
    val filterDefinitionFactory: (name: String, property: KProperty1<*, *>, parentNodeDefinition: NodeDefinition, subFilterGenerator: SubFilterGenerator) -> FilterEntryDefinition
)
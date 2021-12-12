package com.nkcoding.graphglue.graphql.connection.filter

import com.nkcoding.graphglue.graphql.connection.filter.definition.FilterEntryDefinition
import com.nkcoding.graphglue.graphql.connection.filter.definition.SubFilterGenerator
import kotlin.reflect.KClass

/**
 * Wrapper which associates a type with a FilterDefinitionEntry
 */
data class TypeFilterDefinitionEntry(
    val associatedType: KClass<*>,
    val filterDefinitionFactory: (name: String, subFilterGenerator: SubFilterGenerator) -> FilterEntryDefinition
)
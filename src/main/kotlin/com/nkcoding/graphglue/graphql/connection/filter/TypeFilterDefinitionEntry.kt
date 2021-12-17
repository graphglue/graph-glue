package com.nkcoding.graphglue.graphql.connection.filter

import com.nkcoding.graphglue.graphql.connection.filter.definition.FilterEntryDefinition
import com.nkcoding.graphglue.graphql.connection.filter.definition.SubFilterGenerator
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType

/**
 * Wrapper which associates a type with a FilterDefinitionEntry
 */
data class TypeFilterDefinitionEntry(
    val associatedType: KType,
    val filterDefinitionFactory: (name: String, type: KType, subFilterGenerator: SubFilterGenerator) -> FilterEntryDefinition
)
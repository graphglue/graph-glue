package de.graphglue.graphql.connection.filter

import de.graphglue.graphql.connection.filter.definition.FilterEntryDefinition
import de.graphglue.graphql.connection.filter.definition.SubFilterGenerator
import de.graphglue.neo4j.execution.definition.NodeDefinition
import kotlin.reflect.KProperty1
import kotlin.reflect.KType

/**
 * Wrapper which associates a type with a FilterDefinitionEntry
 */
data class TypeFilterDefinitionEntry(
    val associatedType: KType,
    val filterDefinitionFactory: (name: String, property: KProperty1<*, *>, parentNodeDefinition: NodeDefinition, subFilterGenerator: SubFilterGenerator) -> FilterEntryDefinition
)
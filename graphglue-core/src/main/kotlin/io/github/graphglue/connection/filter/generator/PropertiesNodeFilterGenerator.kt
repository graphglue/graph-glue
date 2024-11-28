package io.github.graphglue.connection.filter.generator

import io.github.graphglue.connection.filter.NodeFilterGenerator
import io.github.graphglue.connection.filter.definition.FilterEntryDefinition
import io.github.graphglue.connection.filter.definition.SubFilterGenerator
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.model.FilterProperty
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Provider for filter entries for all properties annotated with [FilterProperty]
 */
class PropertiesNodeFilterGenerator : NodeFilterGenerator {
    override fun generateFilterEntries(
        definition: NodeDefinition,
        subFilterGenerator: SubFilterGenerator
    ): Collection<FilterEntryDefinition> {
        val type = definition.nodeType
        val filterProperties = type.memberProperties.filter { it.hasAnnotation<FilterProperty>() }
        return filterProperties.mapNotNull { subFilterGenerator.filterForProperty(it, definition) }
    }
}
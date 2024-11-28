package io.github.graphglue.connection.filter.generator

import io.github.graphglue.connection.filter.NodeFilterGenerator
import io.github.graphglue.connection.filter.definition.FilterEntryDefinition
import io.github.graphglue.connection.filter.definition.SubFilterGenerator
import io.github.graphglue.connection.filter.definition.SubtypeNodeFilterDefinition
import io.github.graphglue.connection.filter.definition.generateFilterDefinition
import io.github.graphglue.definition.NodeDefinition
import kotlin.reflect.full.isSubclassOf

/**
 * Provider for filter entries based on subtypes
 */
class SubtypeNodeFilterGenerator : NodeFilterGenerator {
    override fun generateFilterEntries(
        definition: NodeDefinition, subFilterGenerator: SubFilterGenerator
    ): Collection<FilterEntryDefinition> {
        val nodeDefinitionCollection = subFilterGenerator.nodeDefinitionCollection
        return nodeDefinitionCollection.filter { it.nodeType.isSubclassOf(definition.nodeType) && it != definition }
            .map {
                SubtypeNodeFilterDefinition(it, generateFilterDefinition(it.nodeType, subFilterGenerator))
            }
    }
}
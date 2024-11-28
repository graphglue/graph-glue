package io.github.graphglue.connection.filter.generator

import io.github.graphglue.connection.filter.NodeFilterGenerator
import io.github.graphglue.connection.filter.definition.AndMetaFilterDefinition
import io.github.graphglue.connection.filter.definition.FilterEntryDefinition
import io.github.graphglue.connection.filter.definition.NotMetaFilterDefinition
import io.github.graphglue.connection.filter.definition.OrMetaFilterDefinition
import io.github.graphglue.connection.filter.definition.SubFilterGenerator
import io.github.graphglue.connection.filter.definition.generateFilterDefinition
import io.github.graphglue.definition.NodeDefinition

/**
 * Provider for meta (and, or, not) filter entries
 */
class MetaNodeFilterGenerator : NodeFilterGenerator {
    override fun generateFilterEntries(
        definition: NodeDefinition,
        subFilterGenerator: SubFilterGenerator
    ): Collection<FilterEntryDefinition> {
        val filterDefinition = generateFilterDefinition(definition.nodeType, subFilterGenerator)
        return listOf(
            AndMetaFilterDefinition(filterDefinition),
            OrMetaFilterDefinition(filterDefinition),
            NotMetaFilterDefinition(filterDefinition)
        )
    }
}
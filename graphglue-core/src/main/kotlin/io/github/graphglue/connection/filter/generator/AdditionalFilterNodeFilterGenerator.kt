package io.github.graphglue.connection.filter.generator

import io.github.graphglue.connection.filter.NodeFilterGenerator
import io.github.graphglue.connection.filter.definition.FilterEntryDefinition
import io.github.graphglue.connection.filter.definition.SubFilterGenerator
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.graphql.extensions.springFindRepeatableAnnotations
import io.github.graphglue.model.AdditionalFilter

/**
 * Provider for additional filter entries based on [AdditionalFilter] annotations
 */
class AdditionalFilterNodeFilterGenerator : NodeFilterGenerator {
    override fun generateFilterEntries(
        definition: NodeDefinition,
        subFilterGenerator: SubFilterGenerator
    ): Collection<FilterEntryDefinition> {
        val type = definition.nodeType
        val additionalFilterAnnotations = type.springFindRepeatableAnnotations<AdditionalFilter>()
        return additionalFilterAnnotations.map {
            subFilterGenerator.additionalFilterBeans[it.beanName]!!
        }
    }
}
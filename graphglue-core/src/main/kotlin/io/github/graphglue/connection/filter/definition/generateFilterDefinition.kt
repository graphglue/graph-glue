package io.github.graphglue.connection.filter.definition

import io.github.graphglue.model.Node
import kotlin.reflect.KClass

/**
 * Generates a [FilterDefinition] for a specific [Node] type
 * Handles retrieving the definition from the cache
 *
 * @param type the [Node] type to generate the filter for
 * @param subFilterGenerator used to generate filter entries
 * @return the generated [FilterDefinition]
 */
fun generateFilterDefinition(
    type: KClass<out Node>,
    subFilterGenerator: SubFilterGenerator
): FilterDefinition<out Node> {

    return subFilterGenerator.filterDefinitionCache.putAndInitIfAbsent(type, FilterDefinition(type)) {
        val nodeDefinition = subFilterGenerator.nodeDefinitionCollection.getNodeDefinition(type)
        val generatedFilters = subFilterGenerator.nodeFilterGenerators.flatMap { generator ->
            generator.generateFilterEntries(nodeDefinition, subFilterGenerator)
        }
        it.init(generatedFilters)
    }
}
package io.github.graphglue.connection.filter.definition

import io.github.graphglue.graphql.extensions.springFindRepeatableAnnotations
import io.github.graphglue.model.AdditionalFilter
import io.github.graphglue.model.FilterProperty
import io.github.graphglue.model.Node
import kotlin.reflect.KClass
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Generates a [FilterDefinition] for a specific [Node] type
 * Handles retrieving the definition from the cache
 *
 * @param type the [Node] type to generate the filter for
 * @param subFilterGenerator used to generate filter entries for properties
 * @return the generated [FilterDefinition]
 */
fun generateFilterDefinition(
    type: KClass<out Node>,
    subFilterGenerator: SubFilterGenerator
): FilterDefinition<out Node> {

    return subFilterGenerator.filterDefinitionCache.putAndInitIfAbsent(type, FilterDefinition(type)) {
        val nodeDefinition = subFilterGenerator.nodeDefinitionCollection.getNodeDefinition(type)
        val filterProperties = type.memberProperties.filter { it.hasAnnotation<FilterProperty>() }
        val filterFields = filterProperties.mapNotNull { subFilterGenerator.filterForProperty(it, nodeDefinition) }
        val additionalFilterAnnotations = type.springFindRepeatableAnnotations<AdditionalFilter>()
        val additionalFilters = additionalFilterAnnotations.map {
            subFilterGenerator.additionalFilterBeans[it.beanName]!!
        }
        val generatedFilters = subFilterGenerator.nodeFilterGenerators.flatMap { generator ->
            generator.generateFilterEntries(nodeDefinition, subFilterGenerator)
        }
        it.init(filterFields + additionalFilters + generatedFilters)
    }
}
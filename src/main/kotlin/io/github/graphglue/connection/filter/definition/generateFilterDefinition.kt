package io.github.graphglue.connection.filter.definition

import io.github.graphglue.connection.filter.FilterProperty
import io.github.graphglue.graphql.extensions.springFindRepeatableAnnotations
import io.github.graphglue.model.AdditionalFilter
import io.github.graphglue.model.Node
import kotlin.reflect.KClass
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties

fun generateFilterDefinition(
    type: KClass<out Node>,
    subFilterGenerator: SubFilterGenerator
): FilterDefinition<out Node> {

    return subFilterGenerator.filterDefinitionCache.putAndInitIfAbsent(type, FilterDefinition(type)) {
        val nodeDefinition = subFilterGenerator.nodeDefinitionCollection.getOrCreate(type)
        val filterProperties = type.memberProperties.filter { it.hasAnnotation<FilterProperty>() }
        val filterFields = filterProperties.map { subFilterGenerator.filterForProperty(it, nodeDefinition) }
        val additionalFilterAnnotations = type.springFindRepeatableAnnotations<AdditionalFilter>()
        val additionalFilters = additionalFilterAnnotations.map {
            subFilterGenerator.additionalFilterBeans[it.beanName]!!
        }
        it.init(filterFields + additionalFilters)
    }
}
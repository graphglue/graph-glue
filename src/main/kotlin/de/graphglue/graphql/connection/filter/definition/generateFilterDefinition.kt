package de.graphglue.graphql.connection.filter.definition

import de.graphglue.graphql.connection.filter.FilterProperty
import de.graphglue.graphql.extensions.springFindRepeatableAnnotations
import de.graphglue.model.AdditionalFilter
import de.graphglue.model.Node
import org.springframework.core.annotation.AnnotatedElementUtils
import kotlin.reflect.KClass
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties

fun generateFilterDefinition(
    type: KClass<out Node>,
    subFilterGenerator: SubFilterGenerator
): FilterDefinition<out Node> {

    return subFilterGenerator.filterDefinitionCache.computeIfAbsent(type) {
        val nodeDefinition = subFilterGenerator.nodeDefinitionCollection.getOrCreate(type)
        val filterProperties = type.memberProperties.filter { it.hasAnnotation<FilterProperty>() }
        val filterFields = filterProperties.map { subFilterGenerator.filterForProperty(it, nodeDefinition) }
        val additionalFilterAnnotations = type.springFindRepeatableAnnotations<AdditionalFilter>()
        val additionalFilters = additionalFilterAnnotations.map {
            subFilterGenerator.additionalFilterBeans[it.beanName]!!
        }

        FilterDefinition(type, filterFields + additionalFilters)
    }
}
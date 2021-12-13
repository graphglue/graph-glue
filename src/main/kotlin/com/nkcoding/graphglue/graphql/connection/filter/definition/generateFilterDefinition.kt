package com.nkcoding.graphglue.graphql.connection.filter.definition

import com.nkcoding.graphglue.graphql.connection.filter.FilterProperty
import com.nkcoding.graphglue.model.Node
import kotlin.reflect.KClass
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure

fun generateFilterDefinition(
    type: KClass<out Node>,
    subFilterGenerator: SubFilterGenerator
): FilterDefinition<out Node> {

    return subFilterGenerator.filterDefinitionCache.computeIfAbsent(type) {
        val filterProperties = type.memberProperties.filter { it.hasAnnotation<FilterProperty>() }
        val filterFields = filterProperties.map { subFilterGenerator.filterForType(it.returnType.jvmErasure, it.name) }
        FilterDefinition(type, filterFields)
    }
}
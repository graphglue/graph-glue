package com.nkcoding.graphglue.graphql.connection.order

import com.nkcoding.graphglue.model.Node
import kotlin.reflect.KClass
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties

fun <T : Node> generateOrders(type: KClass<T>): Map<String, OrderField<T>> {
    val generatedOrders = type.memberProperties.filter { it.hasAnnotation<OrderProperty>() }
        .map { OrderField(it.name, listOf(SimpleOrderPart(it), IdOrderPart)) }
    val allOrders =  generatedOrders + IdOrderField
    return allOrders.associateBy { it.name.toEnumNameCase() }
}

private fun String.toEnumNameCase() = this.replace("(?=[A-Z])".toRegex(), "_").uppercase()
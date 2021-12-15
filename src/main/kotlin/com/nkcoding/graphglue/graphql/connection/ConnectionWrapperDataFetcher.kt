package com.nkcoding.graphglue.graphql.connection

import com.expediagroup.graphql.server.spring.execution.SpringDataFetcher
import com.fasterxml.jackson.databind.ObjectMapper
import com.nkcoding.graphglue.graphql.connection.filter.definition.FilterDefinition
import com.nkcoding.graphglue.graphql.connection.order.IdOrderField
import com.nkcoding.graphglue.graphql.connection.order.Order
import com.nkcoding.graphglue.graphql.connection.order.OrderDirection
import com.nkcoding.graphglue.graphql.connection.order.OrderField
import graphql.schema.DataFetchingEnvironment
import org.springframework.context.ApplicationContext
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

class ConnectionWrapperDataFetcher(
    target: Any?,
    fn: KFunction<*>,
    objectMapper: ObjectMapper,
    applicationContext: ApplicationContext,
    private val filterDefinition: FilterDefinition<*>
) : SpringDataFetcher(target, fn, objectMapper, applicationContext) {
    @ExperimentalStdlibApi
    override fun mapParameterToValue(param: KParameter, environment: DataFetchingEnvironment): Pair<KParameter, Any?>? {
        val name = param.name
        return if (environment.containsArgument(name)) {
            when (name) {
                "filter" -> param to filterDefinition.parseFilter(environment.arguments[name]!!)
                "orderBy" -> param to parseOrder(environment.arguments[name]!!)
                else -> super.mapParameterToValue(param, environment)
            }
        } else {
            super.mapParameterToValue(param, environment)
        }
    }

    private fun parseOrder(value: Any): Order<*> {
        value as Map<*, *>
        val directionName = value["direction"] as String?
        val direction = if (directionName != null) {
            OrderDirection.valueOf(directionName)
        } else {
            OrderDirection.ASC
        }
        return Order(direction, value["field"] as OrderField<*>? ?: IdOrderField)
    }
}
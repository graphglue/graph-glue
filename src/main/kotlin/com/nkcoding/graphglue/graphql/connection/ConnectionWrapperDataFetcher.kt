package com.nkcoding.graphglue.graphql.connection

import com.expediagroup.graphql.server.spring.execution.SpringDataFetcher
import com.fasterxml.jackson.databind.ObjectMapper
import com.nkcoding.graphglue.graphql.connection.filter.definition.FilterDefinition
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
        println(name)
        return if (name == "filter" && environment.containsArgument(name)) {
            param to filterDefinition.parseFilter(environment.arguments[name]!!)
        }  else {
            super.mapParameterToValue(param, environment)
        }
    }
}
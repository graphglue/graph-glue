package de.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import de.graphglue.graphql.connection.order.Order
import de.graphglue.graphql.execution.NodeQuery
import de.graphglue.graphql.extensions.getDataFetcherResult
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import org.springframework.beans.factory.annotation.Autowired

class Edge<T : Node>(private val node: T, private val order: Order<T>) {
    fun node(dataFetchingEnvironment: DataFetchingEnvironment): DataFetcherResult<T> {
        val stepInfo = dataFetchingEnvironment.executionStepInfo
        return dataFetchingEnvironment.getDataFetcherResult(node, "${stepInfo.parent.resultKey}/${stepInfo.resultKey}")
    }

    fun cursor(@Autowired @GraphQLIgnore objectMapper: ObjectMapper): String {
        return order.generateCursor(node, objectMapper)
    }
}
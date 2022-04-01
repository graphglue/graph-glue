package io.github.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import io.github.graphglue.graphql.connection.order.Order
import io.github.graphglue.graphql.extensions.getDataFetcherResult
import org.springframework.beans.factory.annotation.Autowired

/**
 * Edge returned in the GraphQL API
 * Consists of a Node and a Cursor
 * The cursor is only valid in a specific connection context.
 * Cursors can only be used, if the ordering of nodes is not changed.
 * Cursors should not be used for long term storage.
 *
 * @param node the [Node] at the start of the edge
 * @param order necessary for Cursor generation
 */
class Edge<T : Node>(private val node: T, private val order: Order<T>) {

    /**
     * Returns the node of the edge and sets the local context necessary to use caching
     *
     * @param dataFetchingEnvironment defines how the query fetches data
     * @return the node and the local context necessary for caching
     */
    fun node(dataFetchingEnvironment: DataFetchingEnvironment): DataFetcherResult<T> {
        val stepInfo = dataFetchingEnvironment.executionStepInfo
        return dataFetchingEnvironment.getDataFetcherResult(node, "${stepInfo.parent.resultKey}/${stepInfo.resultKey}")
    }

    /**
     * Generates the cursor associated with the edge
     *
     * @param objectMapper necessary for cursor generation
     * @return the generated cursor
     */
    fun cursor(@Autowired @GraphQLIgnore objectMapper: ObjectMapper): String {
        return order.generateCursor(node, objectMapper)
    }
}
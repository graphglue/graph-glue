package io.github.graphglue.connection.model

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.graphglue.connection.order.Order
import io.github.graphglue.model.Node
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
class Edge<T : Node>(val node: T, private val order: Order<T>) {

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
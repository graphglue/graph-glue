package com.nkcoding.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.nkcoding.graphglue.graphql.connection.order.Order
import org.springframework.beans.factory.annotation.Autowired

class Edge<T : Node>(val node: T, private val order: Order<T>) {
    fun cursor(@Autowired @GraphQLIgnore objectMapper: ObjectMapper): String {
        return order.generateCursor(node, objectMapper)
    }
}
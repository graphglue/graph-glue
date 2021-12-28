package com.nkcoding.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.fasterxml.jackson.databind.ObjectMapper
import com.nkcoding.graphglue.graphql.connection.order.Order
import com.nkcoding.graphglue.graphql.execution.QueryOptions

@GraphQLDescription("Information about the current page in a connection")
class PageInfo(
    private val queryOptions: QueryOptions,
    private val nodes: List<Node>,
    private val objectMapper: ObjectMapper
) {

    @Suppress("UNCHECKED_CAST")
    private val orderBy: Order<Node> get() = queryOptions.orderBy as Order<Node>

    @GraphQLDescription("When paginating forwards, the cursor to continue")
    val startCursor: String?
        get() {
            return if (nodes.isEmpty()) {
                null
            } else {
                orderBy.generateCursor(nodes.first(), objectMapper)
            }
        }

    @GraphQLDescription("When paginating backwards, the cursor to continue")
    val endCursor: String?
        get() {
            return if (nodes.isEmpty()) {
                null
            } else {
                orderBy.generateCursor(nodes.last(), objectMapper)
            }
        }

    @GraphQLDescription("When paginating forwards, are there more items?")
    val hasNextPage: Boolean
        get() {
            return if (queryOptions.first != null) {
                nodes.size > queryOptions.first
            } else {
                false
            }
        }

    @GraphQLDescription("When paginating backwards, are there more items?")
    val hasPreviousPage: Boolean
        get() {
            return if (queryOptions.last != null) {
                nodes.size > queryOptions.last
            } else {
                false
            }
        }
}
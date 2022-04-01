package io.github.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.graphglue.db.execution.NodeQueryOptions
import io.github.graphglue.graphql.connection.order.Order

/**
 * Page info used in GraphQL connection to provide general pagination information
 *
 * @param nodeQueryOptions options used for querying the connection
 * @param allNodes the list of all nodes returned from the query, before first or last was applied to limit results
 * @param nodes the list of nodes which is returned
 * @param objectMapper used for cursor generation
 */
@GraphQLDescription("Information about the current page in a connection")
class PageInfo(
    private val nodeQueryOptions: NodeQueryOptions,
    private val allNodes: List<Node>,
    private val nodes: List<Node>,
    private val objectMapper: ObjectMapper
) {

    /**
     * Order in which nodes are sorted, used for cursor generation
     */
    @Suppress("UNCHECKED_CAST")
    private val orderBy: Order<Node>
        get() = nodeQueryOptions.orderBy as Order<Node>

    /**
     * Cursor of the first [Node] in [nodes]
     * When paginating forwards, the cursor to continue
     */
    @GraphQLDescription("When paginating forwards, the cursor to continue")
    val startCursor: String?
        get() {
            return if (nodes.isEmpty()) {
                null
            } else {
                orderBy.generateCursor(nodes.first(), objectMapper)
            }
        }

    /**
     * Cursor of the last [Node] in [nodes]
     * When paginating backwards, the cursor to continue
     */
    @GraphQLDescription("When paginating backwards, the cursor to continue")
    val endCursor: String?
        get() {
            return if (nodes.isEmpty()) {
                null
            } else {
                orderBy.generateCursor(nodes.last(), objectMapper)
            }
        }

    /**
     * When paginating forwards, are there more items?
     * Calculating using [allNodes] and [nodes]
     */
    @GraphQLDescription("When paginating forwards, are there more items?")
    val hasNextPage: Boolean
        get() {
            return if (nodeQueryOptions.first != null) {
                allNodes.size > (nodeQueryOptions.first - 1)
            } else {
                false
            }
        }

    /**
     * When paginating backwards, are there more items?
     * Calculating using [allNodes] and [nodes]
     */
    @GraphQLDescription("When paginating backwards, are there more items?")
    val hasPreviousPage: Boolean
        get() {
            return if (nodeQueryOptions.last != null) {
                allNodes.size > (nodeQueryOptions.last - 1)
            } else {
                false
            }
        }
}
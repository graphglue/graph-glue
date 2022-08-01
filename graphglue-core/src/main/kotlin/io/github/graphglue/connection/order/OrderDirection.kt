package io.github.graphglue.connection.order

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

/**
 * Different directions in which a order can be applied
 */
@GraphQLDescription("Possible direction in which a list of nodes can be ordered")
enum class OrderDirection {
    /**
     * Ascending
     */
    @GraphQLDescription("Ascending")
    ASC,

    /**
     * Descending
     */
    @GraphQLDescription("Descending")
    DESC
}
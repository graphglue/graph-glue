package io.github.graphglue.graphql.connection.order

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("Possible direction in which a list of nodes can be ordered")
enum class OrderDirection {
    @GraphQLDescription("Ascending")
    ASC,

    @GraphQLDescription("Descending")
    DESC
}
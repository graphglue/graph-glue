package com.nkcoding.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("Information about the current page in a connection")
class PageInfo(
    @GraphQLDescription("When paginating forwards, the cursor to continue")
    val startCursor: String?,
    @GraphQLDescription("When paginating backwards, the cursor to continue")
    val endCursor: String?,
    @GraphQLDescription("When paginating forwards, are there more items?")
    val hasNextPage: Boolean?,
    @GraphQLDescription("When paginating backwards, are there more items?")
    val hasPreviousPage: Boolean?
)
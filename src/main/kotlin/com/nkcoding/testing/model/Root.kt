package com.nkcoding.testing.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("What a nice type")
class Root {
    @GraphQLDescription("What a nice id")
    val id = 100
}
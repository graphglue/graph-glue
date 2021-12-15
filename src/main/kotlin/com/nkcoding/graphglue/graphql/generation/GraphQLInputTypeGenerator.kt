package com.nkcoding.graphglue.graphql.generation

import graphql.schema.GraphQLInputType

interface GraphQLInputTypeGenerator {
    fun toGraphQLType(
        objectTypeCache: MutableMap<String, GraphQLInputType>
    ): GraphQLInputType
}
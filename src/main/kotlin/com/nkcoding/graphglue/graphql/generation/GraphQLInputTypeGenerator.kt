package com.nkcoding.graphglue.graphql.generation

import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType

interface GraphQLInputTypeGenerator {
    fun toGraphQLType(
        objectTypeCache: GraphQLTypeCache<GraphQLInputObjectType>,
        codeRegistry: GraphQLCodeRegistry.Builder
    ): GraphQLInputType
}
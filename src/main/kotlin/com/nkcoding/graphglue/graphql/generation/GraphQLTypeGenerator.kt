package com.nkcoding.graphglue.graphql.generation

import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLType

interface GraphQLTypeGenerator {
    fun toGraphQLType(
        objectTypeCache: GraphQLTypeCache<GraphQLObjectType>,
        codeRegistry: GraphQLCodeRegistry.Builder
    ): GraphQLType
}
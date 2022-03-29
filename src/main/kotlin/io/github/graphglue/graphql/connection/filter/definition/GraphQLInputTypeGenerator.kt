package io.github.graphglue.graphql.connection.filter.definition

import io.github.graphglue.util.CacheMap
import graphql.schema.GraphQLInputType

interface GraphQLInputTypeGenerator {
    fun toGraphQLType(
        inputTypeCache: CacheMap<String, GraphQLInputType>
    ): GraphQLInputType
}
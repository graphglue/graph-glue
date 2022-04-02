package io.github.graphglue.connection.filter.definition

import graphql.schema.GraphQLInputType
import io.github.graphglue.util.CacheMap

interface GraphQLInputTypeGenerator {
    fun toGraphQLType(
        inputTypeCache: CacheMap<String, GraphQLInputType>
    ): GraphQLInputType
}
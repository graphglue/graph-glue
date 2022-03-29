package io.github.graphglue.graphql.connection.filter.definition

import de.graphglue.util.CacheMap
import graphql.schema.GraphQLInputType

interface GraphQLInputTypeGenerator {
    fun toGraphQLType(
        inputTypeCache: CacheMap<String, GraphQLInputType>
    ): GraphQLInputType
}
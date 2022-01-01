package de.graphglue.graphql.generation

import de.graphglue.util.CacheMap
import graphql.schema.GraphQLInputType

interface GraphQLInputTypeGenerator {
    fun toGraphQLType(
        inputTypeCache: CacheMap<String, GraphQLInputType>
    ): GraphQLInputType
}
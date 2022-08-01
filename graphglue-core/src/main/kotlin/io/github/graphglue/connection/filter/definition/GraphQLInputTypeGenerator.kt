package io.github.graphglue.connection.filter.definition

import graphql.schema.GraphQLInputType
import io.github.graphglue.util.CacheMap

/**
 * Defines a function to convert into a [GraphQLInputType]
 */
interface GraphQLInputTypeGenerator {
    /**
     * Transforms this into a [GraphQLInputType]
     *
     * @param inputTypeCache cache of already existing input types, should be used to avoid type duplicates
     * @return the generated type which will be used in the schema
     */
    fun toGraphQLType(
        inputTypeCache: CacheMap<String, GraphQLInputType>
    ): GraphQLInputType
}
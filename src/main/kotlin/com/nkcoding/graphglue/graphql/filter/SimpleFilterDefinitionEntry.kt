package com.nkcoding.graphglue.graphql.filter

import com.nkcoding.graphglue.graphql.generation.GraphQLTypeCache
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType

open class SimpleFilterDefinitionEntry(name: String, private val type: GraphQLInputType) : FilterDefinitionEntry(name) {
    override fun toGraphQLType(
        objectTypeCache: GraphQLTypeCache<GraphQLInputObjectType>,
        codeRegistry: GraphQLCodeRegistry.Builder
    ) = type
}
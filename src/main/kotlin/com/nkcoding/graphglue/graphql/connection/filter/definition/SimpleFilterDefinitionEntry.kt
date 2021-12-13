package com.nkcoding.graphglue.graphql.connection.filter.definition

import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType

abstract class SimpleFilterDefinitionEntry(name: String, description: String, private val type: GraphQLInputType) :
    FilterEntryDefinition(name, description) {
    override fun toGraphQLType(
        objectTypeCache: MutableMap<String, GraphQLInputObjectType>,
    ) = type
}
package com.nkcoding.graphglue.graphql.filter

import graphql.schema.GraphQLType

abstract class FilterDefinitionEntry(val name: String) {
    abstract fun toGraphQLType(): GraphQLType
}
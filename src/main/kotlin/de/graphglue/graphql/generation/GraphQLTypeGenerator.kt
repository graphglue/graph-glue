package de.graphglue.graphql.generation

import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLType

interface GraphQLTypeGenerator {
    fun toGraphQLType(
        objectTypeCache: HashMap<String, GraphQLObjectType>,
        codeRegistry: GraphQLCodeRegistry.Builder
    ): GraphQLType
}
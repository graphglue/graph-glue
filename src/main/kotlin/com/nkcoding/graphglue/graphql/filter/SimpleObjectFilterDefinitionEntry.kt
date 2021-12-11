package com.nkcoding.graphglue.graphql.filter

import com.nkcoding.graphglue.graphql.generation.GraphQLTypeCache
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType

open class SimpleObjectFilterDefinitionEntry(
    name: String,
    val typeName: String,
    val fields: List<FilterDefinitionEntry>
) : FilterDefinitionEntry(name) {
    override fun toGraphQLType(
        objectTypeCache: GraphQLTypeCache<GraphQLInputObjectType>,
        codeRegistry: GraphQLCodeRegistry.Builder
    ): GraphQLInputType {
        return objectTypeCache.buildIfNotInCache(typeName) {
            val builder = GraphQLInputObjectType.newInputObject()
            builder.name(typeName)
            for (field in fields) {
                builder.field {
                    it.name(field.name).type(field.toGraphQLType(objectTypeCache, codeRegistry))
                }
            }
            builder.build()
        }
    }
}
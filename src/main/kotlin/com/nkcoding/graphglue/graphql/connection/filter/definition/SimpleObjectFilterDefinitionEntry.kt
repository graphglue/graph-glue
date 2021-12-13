package com.nkcoding.graphglue.graphql.connection.filter.definition

import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType

abstract class SimpleObjectFilterDefinitionEntry(
    name: String,
    description:  String,
    val typeName: String,
    val fields: List<FilterEntryDefinition>
) : FilterEntryDefinition(name, description) {
    override fun toGraphQLType(
        objectTypeCache: MutableMap<String, GraphQLInputObjectType>
    ): GraphQLInputType {
        return objectTypeCache.computeIfAbsent(typeName) {
            val builder = GraphQLInputObjectType.newInputObject()
            builder.name(typeName)
            for (field in fields) {
                builder.field {
                    it.name(field.name).type(field.toGraphQLType(objectTypeCache))
                }
            }
            builder.build()
        }
    }
}
package com.nkcoding.graphglue.graphql.connection.filter.definition

import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType

abstract class SimpleObjectFilterDefinitionEntry<T : FilterEntryDefinition>(
    name: String,
    description:  String,
    val typeName: String,
    fields: List<T>
) : FilterEntryDefinition(name, description) {

    val fields = fields.associateBy { it.name }

    override fun toGraphQLType(
        objectTypeCache: MutableMap<String, GraphQLInputObjectType>
    ): GraphQLInputType {
        return objectTypeCache.computeIfAbsent(typeName) {
            val builder = GraphQLInputObjectType.newInputObject()
            builder.name(typeName)
            for (field in fields.values) {
                builder.field {
                    it.name(field.name).type(field.toGraphQLType(objectTypeCache))
                }
            }
            builder.build()
        }
    }
}
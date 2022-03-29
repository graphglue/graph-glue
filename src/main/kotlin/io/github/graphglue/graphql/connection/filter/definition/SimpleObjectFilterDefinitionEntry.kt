package io.github.graphglue.graphql.connection.filter.definition

import de.graphglue.util.CacheMap
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLTypeReference

abstract class SimpleObjectFilterDefinitionEntry<T : FilterEntryDefinition>(
    name: String,
    description: String,
    val typeName: String,
    fields: List<T>
) : FilterEntryDefinition(name, description) {

    val fields = fields.associateBy { it.name }

    override fun toGraphQLType(
        inputTypeCache: CacheMap<String, GraphQLInputType>
    ): GraphQLInputType {
        return inputTypeCache.computeIfAbsent(typeName, GraphQLTypeReference(typeName)) {
            val builder = GraphQLInputObjectType.newInputObject()
            builder.name(typeName)
            for (field in fields.values) {
                builder.field {
                    it.name(field.name).type(field.toGraphQLType(inputTypeCache))
                }
            }
            builder.build()
        }
    }
}
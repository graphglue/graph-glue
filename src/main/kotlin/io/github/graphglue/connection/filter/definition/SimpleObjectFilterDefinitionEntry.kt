package io.github.graphglue.connection.filter.definition

import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLTypeReference
import io.github.graphglue.util.CacheMap

/**
 * Base lass for [FilterEntryDefinition] which build a filter by joining a list of sub filters ([fields])
 * Creates a filter where any amount of fields (0 to all) can be present
 *
 * @param name the name of the field on the [GraphQLInputObjectType]
 * @param description the description of the field
 * @param typeName name of the constructed [GraphQLInputType] which serves as input for the filter
 * @param fields list of fields for the filter, when evaluating these fields are joined by AND
 */
abstract class SimpleObjectFilterDefinitionEntry<T : FilterEntryDefinition>(
    name: String,
    description: String,
    val typeName: String,
    fields: List<T>
) : FilterEntryDefinition(name, description) {

    /**
     * Fields associated by name
     */
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
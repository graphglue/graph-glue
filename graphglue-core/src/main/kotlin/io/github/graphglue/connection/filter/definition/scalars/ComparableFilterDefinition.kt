package io.github.graphglue.connection.filter.definition.scalars

import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType

/**
 * Filter for a comparable scalar property (e.g. [Int], [String], ...).
 * Defines a list of fields how the property can be filtered (e.g. eq, in, startsWith, ...).
 * If multiple fields are provided, these are joined by AND
 * Already defines `eq`, `in`, `lt`, `lte`, `gt` and `gte` entries
 *
 * @param name the name of the field on the [GraphQLInputObjectType]
 * @param description the description of the field
 * @param typeName name of the constructed [GraphQLInputType] which serves as input for the filter
 * @param scalarType the [GraphQLInputType] for the field inputs (e.g. for eq, startsWith, ...)
 * @param neo4jName the name of the property of the node in the database (might be different from [name])
 * @param nullable if true, the scalar is nullable, otherwise it is non-nullable
 * @param entries additional fields of this filter, define how the property can be filtered (e.g. startsWith, ...)
 */
abstract class ComparableFilterDefinition(
    name: String,
    description: String,
    typeName: String,
    scalarType: GraphQLInputType,
    neo4jName: String,
    nullable: Boolean,
    entries: List<ScalarFilterEntryBase>
) : ScalarFilterDefinition(
    name, description, typeName, scalarType, neo4jName, nullable, entries + getDefaultFilterEntries()
)

/**
 * Provides a default comparable filter entries:
 *
 * @return the list of generated filter entries
 */
private fun getDefaultFilterEntries(): List<ScalarFilterEntry> {
    return listOf(ScalarFilterEntry(
        "lt", "Matches values which are lesser than the provided value"
    ) { property, value ->
        property.lt(value)
    }, ScalarFilterEntry(
        "lte", "Matches values which are lesser than or equal to the provided value"
    ) { property, value ->
        property.lte(value)
    }, ScalarFilterEntry(
        "gt", "Matches values which are greater than the provided value"
    ) { property, value ->
        property.gt(value)
    }, ScalarFilterEntry(
        "gte", "Matches values which are greater than or equal to the provided value"
    ) { property, value ->
        property.gte(value)
    })
}
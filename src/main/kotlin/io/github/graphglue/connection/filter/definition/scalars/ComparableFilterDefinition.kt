package io.github.graphglue.connection.filter.definition.scalars

import graphql.schema.GraphQLInputType

abstract class ComparableFilterDefinition<T>(
    name: String,
    description: String,
    typeName: String,
    scalarType: GraphQLInputType,
    neo4jName: String,
    entries: List<ScalarFilterEntryBase<T>>
) : ScalarFilterDefinition<T>(
    name,
    description,
    typeName,
    scalarType,
    neo4jName,
    entries + getDefaultFilterEntries<T>()
)

private fun <T> getDefaultFilterEntries(): List<ScalarFilterEntry<T>> {
    return listOf(
        ScalarFilterEntry(
            "lt",
            "Matches values which are lesser than the provided value"
        ) { property, value ->
            property.lt(value)
        },
        ScalarFilterEntry(
            "lte",
            "Matches values which are lesser than or equal to the provided value"
        ) { property, value ->
            property.lte(value)
        },
        ScalarFilterEntry(
            "gt",
            "Matches values which are greater than the provided value"
        ) { property, value ->
            property.gt(value)
        },
        ScalarFilterEntry(
            "gte",
            "Matches values which are greater than or equal to the provided value"
        ) { property, value ->
            property.gte(value)
        }
    )
}
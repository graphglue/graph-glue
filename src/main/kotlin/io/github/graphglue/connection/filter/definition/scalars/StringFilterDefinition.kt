package io.github.graphglue.connection.filter.definition.scalars

import graphql.Scalars

/**
 * Filter definition for a [String] property
 *
 * @param name the name of the field in the filter
 * @param neo4jName the name of the property in the database
 * @param nullable if true, the scalar is nullable, otherwise it is non-nullable
 */
class StringFilterDefinition(name: String, neo4jName: String, nullable: Boolean) : ComparableFilterDefinition(
    name,
    "Filter which can be used to filter for Nodes with a specific String field",
    "String",
    Scalars.GraphQLString,
    neo4jName,
    nullable,
    listOf(ScalarFilterEntry(
        "startsWith", "Matches Strings which start with the provided value"
    ) { property, value ->
        property.startsWith(value)
    }, ScalarFilterEntry(
        "endsWith", "Matches Strings which end with the provided value"
    ) { property, value ->
        property.endsWith(value)
    }, ScalarFilterEntry(
        "contains", "Matches Strings which contain the provided value"
    ) { property, value ->
        property.contains(value)
    }, ScalarFilterEntry(
        "matches", "Matches Strings using the provided RegEx"
    ) { property, value ->
        property.matches(value)
    })
)
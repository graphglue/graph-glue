package io.github.graphglue.connection.filter.definition.scalars

import graphql.Scalars

/**
 * Filter definition for a [Int] property
 *
 * @param name the name of the field in the filter
 * @param neo4jName the name of the property in the database
 * @param nullable if true, the scalar is nullable, otherwise it is non-nullable
 */
class IntFilterDefinition(name: String, neo4jName: String, nullable: Boolean) : ComparableFilterDefinition(
    name,
    "Filter which can be used to filter for Nodes with a specific Int field",
    "Int",
    Scalars.GraphQLInt,
    neo4jName,
    nullable,
    emptyList()
)
package io.github.graphglue.connection.filter.definition.scalars

import graphql.Scalars

/**
 * Filter definition for a [Int] property
 *
 * @param name the name of the field in the filter
 * @param neo4jName the name of the property in the database
 */
class IntFilterDefinition(name: String, neo4jName: String) :
    ComparableFilterDefinition(
        name,
        "Filter which can be used to filter for Nodes with a specific Int field",
        "IntFilterInput",
        Scalars.GraphQLInt,
        neo4jName,
        emptyList()
    )
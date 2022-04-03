package io.github.graphglue.connection.filter.definition.scalars

import graphql.Scalars

/**
 * Filter definition for a [Boolean] property
 *
 * @param name the name of the field in the filter
 * @param neo4jName the name of the property in the database
 */
class BooleanFilterDefinition(name: String, neo4jName: String) :
    ScalarFilterDefinition<Boolean>(
        name,
        "Filter which can be used to filter for Nodes with a specific Boolean field",
        "BooleanFilterInput",
        Scalars.GraphQLBoolean,
        neo4jName,
        emptyList()
    )
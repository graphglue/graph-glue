package io.github.graphglue.connection.filter.definition.scalars

import graphql.Scalars

class FloatFilterDefinition(name: String, neo4jName: String) :
    ComparableFilterDefinition<Float>(
        name,
        "Filter which can be used to filter for Nodes with a specific Float field",
        "FloatFilterInput",
        Scalars.GraphQLFloat,
        neo4jName,
        emptyList()
    )
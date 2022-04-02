package io.github.graphglue.connection.filter.definition.scalars

import graphql.Scalars

class IntFilterDefinition(name: String, neo4jName: String) :
    ComparableFilterDefinition<Int>(
        name,
        "Filter which can be used to filter for Nodes with a specific Int field",
        "IntFilterInput",
        Scalars.GraphQLInt,
        neo4jName,
        emptyList()
    )
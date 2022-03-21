package de.graphglue.graphql.connection.filter.definition.scalars

import graphql.Scalars

class BooleanFilterDefinition(name: String, neo4jName: String) :
    ScalarFilterDefinition<Boolean>(
        name,
        "Filter which can be used to filter for Nodes with a specific Boolean field",
        "BooleanFilterInput",
        Scalars.GraphQLBoolean,
        neo4jName,
        emptyList()
    )
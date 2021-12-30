package com.nkcoding.graphglue.graphql.connection.filter.definition.scalars

import graphql.Scalars

class StringFilterDefinition(name: String, neo4jName: String) :
    ScalarFilterDefinition<String>(
        name,
        "Filter which can be used to filter for Nodes with a specific String field",
        "StringFilterInput",
        Scalars.GraphQLString,
        neo4jName,
        listOf(
            ScalarFilterEntry(
                "startsWith",
                "Matches Strings which start with the provided value"
            ) { property, value ->
                property.startsWith(value)
            },
            ScalarFilterEntry(
                "endsWith",
                "Matches Strings which end with the provided value"
            ) { property, value ->
                property.endsWith(value)
            },
            ScalarFilterEntry(
                "contains",
                "Matches Strings which contain the provided value"
            ) { property, value ->
                property.contains(value)
            },
            ScalarFilterEntry(
                "matches",
                "Matches Strings using the provided RegEx"
            ) { property, value ->
                property.matches(value)
            }
        )
    )
package com.nkcoding.graphglue.graphql.connection.filter.definition.scalars

import com.expediagroup.graphql.generator.scalars.ID
import graphql.Scalars

class IDFilterDefinition(name: String, neo4jName: String) :
    ScalarFilterDefinition<ID>(
        name,
        "Filter which can be used to filter for Nodes with a specific ID field",
        "IDFilterInput",
        Scalars.GraphQLID,
        neo4jName,
        emptyList()
    )
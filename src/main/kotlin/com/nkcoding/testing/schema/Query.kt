package com.nkcoding.testing.schema

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.server.operations.Query
import com.fasterxml.jackson.databind.ObjectMapper
import com.nkcoding.graphglue.graphql.execution.QueryOptions
import com.nkcoding.graphglue.graphql.execution.QueryParser
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinitionCollection
import com.nkcoding.graphglue.graphql.execution.definition.getNodeDefinition
import com.nkcoding.graphglue.model.Node
import com.nkcoding.graphglue.neo4j.execution.NodeQueryExecutor
import com.nkcoding.testing.model.*
import graphql.schema.DataFetchingEnvironment
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.neo4j.core.Neo4jClient
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import org.springframework.stereotype.Component
import java.util.*

@Component
class Query : Query {

    fun node(
        id: String,
        dfe: DataFetchingEnvironment,
        @Autowired @GraphQLIgnore queryParser: QueryParser,
        @Autowired @GraphQLIgnore mappingContext: Neo4jMappingContext,
        @Autowired @GraphQLIgnore neo4jClient: Neo4jClient
    ): Node {
        val parsedQuery = queryParser.generateOneNodeQuery(
            queryParser.nodeDefinitionCollection.getNodeDefinition<Node>(),
            dfe,
            emptyList()
        )
        val executor = NodeQueryExecutor(parsedQuery, neo4jClient, mappingContext)
        println(executor.parseQuery())
        TODO()
    }

}
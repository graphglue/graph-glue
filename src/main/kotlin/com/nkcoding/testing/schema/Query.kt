package com.nkcoding.testing.schema

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.server.operations.Query
import com.nkcoding.graphglue.graphql.execution.QueryParser
import com.nkcoding.graphglue.model.Node
import graphql.schema.DataFetchingEnvironment
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.neo4j.core.Neo4jClient
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import org.springframework.stereotype.Component

@Component
class Query : Query {

    fun node2(
        id: String,
        dfe: DataFetchingEnvironment,
        @Autowired @GraphQLIgnore queryParser: QueryParser,
        @Autowired @GraphQLIgnore mappingContext: Neo4jMappingContext,
        @Autowired @GraphQLIgnore neo4jClient: Neo4jClient,
        @Autowired @GraphQLIgnore movieRepository: MovieRepository
    ): Node {
        val movie = movieRepository.findAll().first()
        return movie
    }

}
package com.nkcoding.graphglue.graphql.query

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.nkcoding.graphglue.graphql.execution.QueryParser
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinition
import com.nkcoding.graphglue.model.Connection
import com.nkcoding.graphglue.model.Node
import com.nkcoding.graphglue.neo4j.LazyLoadingContext
import com.nkcoding.graphglue.neo4j.execution.NodeQueryExecutor
import com.nkcoding.graphglue.neo4j.execution.NodeQueryResult
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import org.springframework.beans.factory.annotation.Autowired

class TopLevelQueryProvider<T : Node>(private val nodeDefinition: NodeDefinition) {

    @Suppress("UNCHECKED_CAST")
    fun getFromGraphQL(
        @Autowired @GraphQLIgnore
        queryParser: QueryParser,
        dataFetchingEnvironment: DataFetchingEnvironment,
        @Autowired @GraphQLIgnore
        lazyLoadingContext: LazyLoadingContext,
        @Autowired @GraphQLIgnore
        objectMapper: ObjectMapper
    ): DataFetcherResult<Connection<T>> {
        val nodeQuery = queryParser.generateManyNodeQuery(nodeDefinition, dataFetchingEnvironment, emptyList())
        val queryExecutor =
            NodeQueryExecutor(nodeQuery, lazyLoadingContext.neo4jClient, lazyLoadingContext.neo4jMappingContext)
        val queryResult = queryExecutor.execute() as NodeQueryResult<T>
        return DataFetcherResult.newResult<Connection<T>>()
            .data(Connection.fromQueryResult(queryResult, objectMapper))
            .localContext(nodeQuery)
            .build()
    }

}
package de.graphglue.graphql.query

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import de.graphglue.graphql.extensions.authorizationContext
import de.graphglue.model.Connection
import de.graphglue.model.Node
import de.graphglue.neo4j.LazyLoadingContext
import de.graphglue.neo4j.execution.NodeQueryExecutor
import de.graphglue.neo4j.execution.NodeQueryParser
import de.graphglue.neo4j.execution.NodeQueryResult
import de.graphglue.neo4j.execution.definition.NodeDefinition
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import org.springframework.beans.factory.annotation.Autowired

class TopLevelQueryProvider<T : Node>(private val nodeDefinition: NodeDefinition) {

    @Suppress("UNCHECKED_CAST")
    suspend fun getFromGraphQL(
        @Autowired @GraphQLIgnore
        nodeQueryParser: NodeQueryParser,
        dataFetchingEnvironment: DataFetchingEnvironment,
        @Autowired @GraphQLIgnore
        lazyLoadingContext: LazyLoadingContext,
        @Autowired @GraphQLIgnore
        objectMapper: ObjectMapper
    ): DataFetcherResult<Connection<T>> {
        val nodeQuery = nodeQueryParser.generateManyNodeQuery(
            nodeDefinition,
            dataFetchingEnvironment,
            emptyList(),
            dataFetchingEnvironment.authorizationContext
        )
        val queryExecutor =
            NodeQueryExecutor(nodeQuery, lazyLoadingContext.neo4jClient, lazyLoadingContext.neo4jMappingContext)
        val queryResult = queryExecutor.execute() as NodeQueryResult<T>
        return DataFetcherResult.newResult<Connection<T>>()
            .data(Connection.fromQueryResult(queryResult, objectMapper))
            .localContext(nodeQuery)
            .build()
    }

}
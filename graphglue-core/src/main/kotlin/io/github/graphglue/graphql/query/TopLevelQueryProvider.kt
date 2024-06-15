package io.github.graphglue.graphql.query

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import io.github.graphglue.connection.model.Connection
import io.github.graphglue.data.execution.*
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.graphql.extensions.requiredPermission
import io.github.graphglue.model.Node
import org.springframework.beans.factory.annotation.Autowired

/**
 * Provider for top level queries for specific [Node] types
 * Used to generate connection like top level queries
 *
 * @param T the type of node for which to create the query
 * @param nodeDefinition the definition of the [Node] type to query for
 */
class TopLevelQueryProvider<T : Node>(private val nodeDefinition: NodeDefinition) {

    /**
     * Handles the query for the specific [Node] type
     *
     * @param nodeQueryParser used to parse the query
     * @param dataFetchingEnvironment necessary to generate the node query, used for caching
     * @param nodeQueryEngine used to execute the query
     * @param objectMapper necessary for cursor encoding and decoding
     * @return the result with the correct local context
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun getNodeQuery(
        @Autowired @GraphQLIgnore
        nodeQueryParser: NodeQueryParser,
        dataFetchingEnvironment: DataFetchingEnvironment,
        @Autowired @GraphQLIgnore
        nodeQueryEngine: NodeQueryEngine,
        @Autowired @GraphQLIgnore
        objectMapper: ObjectMapper
    ): DataFetcherResult<Connection<T>> {
        val nodeQuery = nodeQueryParser.generateManyNodeQuery(
            nodeDefinition,
            dataFetchingEnvironment,
            emptyList(),
            dataFetchingEnvironment.requiredPermission
        )

        val queryResult = nodeQueryEngine.execute(nodeQuery) as NodeQueryResult<T>
        return DataFetcherResult.newResult<Connection<T>>()
            .data(Connection.fromQueryResult(queryResult, objectMapper))
            .localContext(nodeQuery)
            .build()
    }

    /**
     * Handles the search query for the specific [Node] type
     *
     * @param nodeQueryParser used to parse the query
     * @param dataFetchingEnvironment necessary to generate the node query, used for caching
     * @param nodeQueryEngine used to execute the query
     * @return the result with the correct local context
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun getSearchQuery(
        @Autowired @GraphQLIgnore
        nodeQueryParser: NodeQueryParser,
        dataFetchingEnvironment: DataFetchingEnvironment,
        @Autowired @GraphQLIgnore
        nodeQueryEngine: NodeQueryEngine
    ): DataFetcherResult<List<T>> {
        val searchQuery = nodeQueryParser.generateSearchQuery(
            nodeDefinition,
            dataFetchingEnvironment,
            dataFetchingEnvironment.requiredPermission
        )

        val queryResult = nodeQueryEngine.execute(searchQuery) as SearchQueryResult<T>
        return DataFetcherResult.newResult<List<T>>()
            .data(queryResult.nodes)
            .localContext(searchQuery.parts[DEFAULT_PART_ID])
            .build()
    }

}
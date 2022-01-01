package de.graphglue.graphql.query

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.generator.scalars.ID
import com.expediagroup.graphql.server.operations.Query
import de.graphglue.graphql.execution.DEFAULT_PART_ID
import de.graphglue.graphql.execution.QueryParser
import de.graphglue.graphql.execution.definition.NodeDefinition
import de.graphglue.model.Node
import de.graphglue.neo4j.CypherConditionGenerator
import de.graphglue.neo4j.LazyLoadingContext
import de.graphglue.neo4j.execution.NodeQueryExecutor
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import org.neo4j.cypherdsl.core.Cypher
import org.springframework.beans.factory.annotation.Autowired

class GraphglueQuery(private val nodeDefinition: NodeDefinition) : Query {
    @GraphQLDescription("Get a Node by id")
    suspend fun node(
        @GraphQLDescription("The id of the node to get") id: ID,
        @Autowired @GraphQLIgnore
        queryParser: QueryParser,
        dataFetchingEnvironment: DataFetchingEnvironment,
        @Autowired @GraphQLIgnore
        lazyLoadingContext: LazyLoadingContext
    ): DataFetcherResult<Node?> {
        val idConditionGenerator = CypherConditionGenerator {
            it.property("id").isEqualTo(Cypher.anonParameter(id.value))
        }
        val nodeQuery =
            queryParser.generateOneNodeQuery(nodeDefinition, dataFetchingEnvironment, listOf(idConditionGenerator))
        val queryExecutor =
            NodeQueryExecutor(nodeQuery, lazyLoadingContext.neo4jClient, lazyLoadingContext.neo4jMappingContext)
        val queryResult = queryExecutor.execute()
        return DataFetcherResult.newResult<Node?>()
            .data(queryResult.nodes.firstOrNull())
            .localContext(nodeQuery.parts[DEFAULT_PART_ID])
            .build()
    }
}
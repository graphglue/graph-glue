package io.github.graphglue.graphql.query

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.generator.scalars.ID
import com.expediagroup.graphql.server.operations.Query
import io.github.graphglue.graphql.extensions.authorizationContext
import io.github.graphglue.model.Node
import io.github.graphglue.neo4j.CypherConditionGenerator
import io.github.graphglue.neo4j.LazyLoadingContext
import io.github.graphglue.neo4j.execution.DEFAULT_PART_ID
import io.github.graphglue.neo4j.execution.NodeQueryExecutor
import io.github.graphglue.neo4j.execution.NodeQueryParser
import io.github.graphglue.neo4j.execution.definition.NodeDefinition
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import org.neo4j.cypherdsl.core.Cypher
import org.springframework.beans.factory.annotation.Autowired

class GraphglueQuery(private val nodeDefinition: NodeDefinition) : Query {
    @GraphQLDescription("Get a Node by id")
    suspend fun node(
        @GraphQLDescription("The id of the node to get") id: ID,
        @Autowired @GraphQLIgnore
        nodeQueryParser: NodeQueryParser,
        dataFetchingEnvironment: DataFetchingEnvironment,
        @Autowired @GraphQLIgnore
        lazyLoadingContext: LazyLoadingContext
    ): DataFetcherResult<Node?> {
        val idConditionGenerator = CypherConditionGenerator {
            it.property("id").isEqualTo(Cypher.anonParameter(id.value))
        }
        val nodeQuery = nodeQueryParser.generateOneNodeQuery(
            nodeDefinition,
            dataFetchingEnvironment,
            listOf(idConditionGenerator),
            dataFetchingEnvironment.authorizationContext
        )
        val queryExecutor =
            NodeQueryExecutor(nodeQuery, lazyLoadingContext.neo4jClient, lazyLoadingContext.neo4jMappingContext)
        val queryResult = queryExecutor.execute()
        return DataFetcherResult.newResult<Node?>()
            .data(queryResult.nodes.firstOrNull())
            .localContext(nodeQuery.parts[DEFAULT_PART_ID])
            .build()
    }
}
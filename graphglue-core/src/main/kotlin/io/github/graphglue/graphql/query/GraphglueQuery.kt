package io.github.graphglue.graphql.query

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.generator.scalars.ID
import com.expediagroup.graphql.server.operations.Query
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import io.github.graphglue.data.LazyLoadingContext
import io.github.graphglue.data.execution.*
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.graphql.extensions.requiredPermission
import io.github.graphglue.model.Node
import org.neo4j.cypherdsl.core.Cypher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext

/**
 * [Query] used to provide the `node` query, which allows for querying [Node]s by id
 *
 * @param nodeDefinition the definition of the [Node] class
 */
class GraphglueQuery(private val nodeDefinition: NodeDefinition) : Query {

    /**
     * `node` query which finds a node by id
     *
     * @param nodeQueryParser used to parse the query
     * @param dataFetchingEnvironment necessary to generate the node query, used for caching
     * @param nodeQueryEngine used to execute the query
     * @return the result with the correct local context
     */
    @GraphQLDescription("Get a Node by id")
    suspend fun node(
        @GraphQLDescription("The id of the node to get") id: ID,
        @Autowired @GraphQLIgnore nodeQueryParser: NodeQueryParser,
        dataFetchingEnvironment: DataFetchingEnvironment,
        @Autowired @GraphQLIgnore nodeQueryEngine: NodeQueryEngine
    ): DataFetcherResult<Node?> {
        val idConditionGenerator = CypherConditionGenerator {
            it.property("id").isEqualTo(Cypher.anonParameter(id.value))
        }
        val nodeQuery = nodeQueryParser.generateOneNodeQuery(
            nodeDefinition,
            dataFetchingEnvironment,
            listOf(idConditionGenerator),
            dataFetchingEnvironment.requiredPermission
        )

        val queryResult = nodeQueryEngine.execute(nodeQuery)
        return DataFetcherResult.newResult<Node?>().data(queryResult.nodes.firstOrNull())
            .localContext(nodeQuery.parts[DEFAULT_PART_ID]).build()
    }
}
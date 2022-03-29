package io.github.graphglue.neo4j

import io.github.graphglue.neo4j.execution.NodeQueryParser
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext

/**
 * Context used to lazily load nodes
 *
 * @param neo4jClient client used to perform Cypher queries
 * @param neo4jMappingContext context used to get mapping functions
 * @param nodeQueryParser used to generate the Cypher query
 */
class LazyLoadingContext(
    val neo4jClient: ReactiveNeo4jClient,
    val neo4jMappingContext: Neo4jMappingContext,
    val nodeQueryParser: NodeQueryParser
)
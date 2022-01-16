package de.graphglue.neo4j

import de.graphglue.neo4j.execution.QueryParser
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext

/**
 * Context used to lazily load nodes
 *
 * @property neo4jClient client used to perform Cypher queries
 * @property neo4jMappingContext context used to get mapping functions
 * @property queryParser used to generate the Cypher query
 */
class LazyLoadingContext(
    val neo4jClient: ReactiveNeo4jClient,
    val neo4jMappingContext: Neo4jMappingContext,
    val queryParser: QueryParser
)
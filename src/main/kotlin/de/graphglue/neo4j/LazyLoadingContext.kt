package de.graphglue.neo4j

import de.graphglue.graphql.execution.QueryParser
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext

class LazyLoadingContext(
    val neo4jClient: ReactiveNeo4jClient,
    val neo4jMappingContext: Neo4jMappingContext,
    val queryParser: QueryParser
)
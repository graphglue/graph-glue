package io.github.graphglue.data

import io.github.graphglue.data.execution.NodeQueryEngine
import io.github.graphglue.data.execution.NodeQueryParser
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext

/**
 * Context used to lazily load nodes
 *
 * @param nodeQueryParser used to generate the Cypher query
 * @param nodeQueryEngine used to execute the Cypher query
 */
class LazyLoadingContext(
    val nodeQueryParser: NodeQueryParser,
    val nodeQueryEngine: NodeQueryEngine
)
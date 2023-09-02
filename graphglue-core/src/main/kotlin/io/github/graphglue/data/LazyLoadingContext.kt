package io.github.graphglue.data

import io.github.graphglue.data.execution.NodeQueryEngine
import io.github.graphglue.data.execution.NodeQueryParser

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
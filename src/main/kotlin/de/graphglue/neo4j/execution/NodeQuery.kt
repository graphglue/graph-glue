package de.graphglue.neo4j.execution

import de.graphglue.neo4j.execution.definition.NodeDefinition

class NodeQuery(
    val definition: NodeDefinition,
    val options: QueryOptions,
    val parts: Map<String, NodeQueryPart>
)
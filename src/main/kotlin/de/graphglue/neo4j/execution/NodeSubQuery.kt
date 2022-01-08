package de.graphglue.neo4j.execution

import de.graphglue.neo4j.execution.definition.NodeDefinition
import de.graphglue.neo4j.execution.definition.RelationshipDefinition

data class NodeSubQuery(
    val query: NodeQuery,
    val onlyOnTypes: List<NodeDefinition>,
    val relationshipDefinition: RelationshipDefinition,
    val resultKey: String
)
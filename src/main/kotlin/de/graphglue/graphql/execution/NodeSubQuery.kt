package de.graphglue.graphql.execution

import de.graphglue.graphql.execution.definition.NodeDefinition
import de.graphglue.graphql.execution.definition.RelationshipDefinition

data class NodeSubQuery(
    val query: NodeQuery,
    val onlyOnTypes: List<NodeDefinition>,
    val relationshipDefinition: RelationshipDefinition,
    val resultKey: String
)
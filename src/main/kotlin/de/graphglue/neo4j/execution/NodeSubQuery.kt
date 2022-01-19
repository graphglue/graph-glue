package de.graphglue.neo4j.execution

import de.graphglue.neo4j.execution.definition.NodeDefinition
import de.graphglue.neo4j.execution.definition.RelationshipDefinition

/**
 * Defines a SubQuery which is part of a [NodeQuery]
 * Must only be evaluated if parent type is in `onlyOnTypes`
 *
 * @property  query the subquery
 * @property onlyOnTypes a list of parent types on which this should be evaluated
 * @property relationshipDefinition defines the relationship to fetch
 * @property resultKey used to identify the result
 */
data class NodeSubQuery(
    val query: NodeQuery,
    val onlyOnTypes: List<NodeDefinition>,
    val relationshipDefinition: RelationshipDefinition,
    val resultKey: String
)
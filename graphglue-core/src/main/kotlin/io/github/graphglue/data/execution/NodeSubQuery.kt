package io.github.graphglue.data.execution

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.definition.RelationshipDefinition

/**
 * Defines a SubQuery which is part of a [NodeQuery]
 * Must only be evaluated if parent type is in `onlyOnTypes`
 *
 * @param  query the subquery
 * @param onlyOnTypes a list of parent types on which this should be evaluated
 * @param relationshipDefinition defines the relationship to fetch
 * @param resultKey used to identify the result
 */
data class NodeSubQuery(
    val query: NodeQuery,
    val onlyOnTypes: List<NodeDefinition>,
    val relationshipDefinition: RelationshipDefinition,
    val resultKey: String
)
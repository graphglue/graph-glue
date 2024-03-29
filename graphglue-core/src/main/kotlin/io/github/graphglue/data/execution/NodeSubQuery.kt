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
class NodeSubQuery(
    val query: NodeQuery,
    onlyOnTypes: List<NodeDefinition>,
    val relationshipDefinition: RelationshipDefinition,
    resultKey: String
) : NodeQueryPartEntry(onlyOnTypes, resultKey) {

    /**
     * The cost of the underlying [query]
     */
    override val cost: Int get() = query.cost
}
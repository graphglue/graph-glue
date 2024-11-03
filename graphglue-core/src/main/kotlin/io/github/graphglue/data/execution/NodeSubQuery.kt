package io.github.graphglue.data.execution

import io.github.graphglue.definition.FieldDefinition
import io.github.graphglue.definition.NodeDefinition

/**
 * Defines a SubQuery which is part of a [NodeQuery]
 * Must only be evaluated if parent type is in `onlyOnTypes`
 *
 * @param definition definition of the queried field
 * @param  query the subquery
 * @param onlyOnTypes a list of parent types on which this should be evaluated
 * @param relationshipDefinitions defines the chain of relationships to fetch
 * @param resultKeyPath the path to the key which fetches this field
 */
class NodeSubQuery(
    definition: FieldDefinition,
    val query: NodeQuery,
    onlyOnTypes: List<NodeDefinition>?,
    val relationshipDefinitions: List<AuthorizedRelationDefinition>,
    resultKeyPath: String
) : NodeQueryEntry<FieldDefinition>(onlyOnTypes, resultKeyPath, definition) {

    /**
     * The cost of the underlying [query]
     */
    override val cost: Int get() = query.cost
}
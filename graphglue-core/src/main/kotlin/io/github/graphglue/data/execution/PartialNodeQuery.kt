package io.github.graphglue.data.execution

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.model.Node

/**
 * Defines a partial query which fetches Nodes of type `definition`
 *
 * @param definition defines which type of [Node] is fetched
 * @param parts subqueries partitioned into parts
 */
class PartialNodeQuery(
    definition: NodeDefinition,
    parts: Map<String, NodeQueryPart>
) : QueryBase<PartialNodeQuery>(definition, parts) {

    override fun copyWithParts(parts: Map<String, NodeQueryPart>): PartialNodeQuery {
        return PartialNodeQuery(definition, parts)
    }
}
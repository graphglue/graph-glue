package io.github.graphglue.data.execution

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.model.Node

/**
 * Defines a partial query which fetches Nodes of type `definition`
 *
 * @param definition defines which type of [Node] is fetched
 * @param entries parts of the query to execute with this query
 */
class PartialNodeQuery(
    definition: NodeDefinition,
    entries: List<NodeQueryEntry<*>>
) : QueryBase<PartialNodeQuery>(definition, entries) {

    override fun copyWithEntries(entries: List<NodeQueryEntry<*>>): PartialNodeQuery {
        return PartialNodeQuery(definition, entries)
    }
}
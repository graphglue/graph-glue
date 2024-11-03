package io.github.graphglue.data.execution

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.model.Node

/**
 * Defines a query which fetches Nodes of type `definition`
 *
 * @param definition defines which type of [Node] is fetched
 * @param options options for the query, e.g. pagination
 * @param entries parts of the query to execute with this query
 */
class NodeQuery(
    definition: NodeDefinition,
    val options: NodeQueryOptions,
    entries: List<NodeQueryEntry<*>>
) : QueryBase<NodeQuery>(definition, entries) {

    override fun copyWithEntries(entries: List<NodeQueryEntry<*>>): NodeQuery {
        return NodeQuery(definition, options, entries)
    }
}
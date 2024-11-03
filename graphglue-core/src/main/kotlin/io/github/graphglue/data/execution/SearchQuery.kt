package io.github.graphglue.data.execution

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.model.Node


/**
 * Defines a search query which fetches Nodes of type `definition`
 *
 * @param definition defines which type of [Node] is fetched
 * @param options options for the query, e.g. pagination
 * @param entries parts of the query to execute with this query
 */
class SearchQuery(
    definition: NodeDefinition,
    val options: SearchQueryOptions,
    entries: List<NodeQueryEntry<*>>
) : QueryBase<SearchQuery>(definition, entries) {

    override fun copyWithEntries(entries: List<NodeQueryEntry<*>>): SearchQuery {
        return SearchQuery(definition, options, entries)
    }
}
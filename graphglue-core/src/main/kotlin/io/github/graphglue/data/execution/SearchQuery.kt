package io.github.graphglue.data.execution

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.model.Node


/**
 * Defines a search query which fetches Nodes of type `definition`
 *
 * @param definition defines which type of [Node] is fetched
 * @param options options for the query, e.g. pagination
 * @param parts subqueries partitioned into parts
 */
class SearchQuery(
    definition: NodeDefinition,
    val options: SearchQueryOptions,
    parts: Map<String, NodeQueryPart>
) : QueryBase<SearchQuery>(definition, parts) {

    override fun copyWithParts(parts: Map<String, NodeQueryPart>): SearchQuery {
        return SearchQuery(definition, options, parts)
    }
}
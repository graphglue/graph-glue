package io.github.graphglue.data.execution

import io.github.graphglue.model.Node

/**
 * Result of a [SearchQuery]
 *
 * @param options options of the query
 * @param nodes the fetched nodes
 */
data class SearchQueryResult<T : Node?>(val options: SearchQueryOptions, val nodes: List<T>)
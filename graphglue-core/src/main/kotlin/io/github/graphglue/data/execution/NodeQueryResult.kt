package io.github.graphglue.data.execution

import io.github.graphglue.model.Node

/**
 * Result of a [NodeQuery]
 *
 * @param options options of the query
 * @param nodes the fetched nodes
 * @param totalCount if fetched, the total amount of nodes before pagination defined in `options`
 */
data class NodeQueryResult<T : Node?>(val options: NodeQueryOptions, val nodes: List<T>, val totalCount: Int?)

package de.graphglue.neo4j.execution

import de.graphglue.model.Node

/**
 * Result of a [NodeQuery]
 *
 * @property options options of the query
 * @property nodes the fetched nodes
 * @property totalCount if fetched, the total amount of nodes before pagination defined in `options`
 */
data class NodeQueryResult<T : Node?>(val options: QueryOptions, val nodes: List<T>, val totalCount: Int?)

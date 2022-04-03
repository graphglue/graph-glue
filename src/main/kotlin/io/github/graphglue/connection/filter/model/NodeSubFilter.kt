package io.github.graphglue.connection.filter.model

import io.github.graphglue.connection.filter.definition.NodeSubFilterDefinition
import org.neo4j.cypherdsl.core.Node

/**
 * [FilterEntry] which allows using a [Filter] to generate the condition
 *
 * @param definition associated definition of the entry
 * @param filter the [Filter] which defines the condition
 */
class NodeSubFilter(definition: NodeSubFilterDefinition, val filter: Filter) : FilterEntry(definition) {
    override fun generateCondition(node: Node) = filter.generateCondition(node)
}
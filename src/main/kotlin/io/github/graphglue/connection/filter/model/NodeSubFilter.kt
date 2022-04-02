package io.github.graphglue.connection.filter.model

import io.github.graphglue.connection.filter.definition.NodeSubFilterDefinition
import org.neo4j.cypherdsl.core.Node

class NodeSubFilter(definition: NodeSubFilterDefinition, val filter: Filter) : FilterEntry(definition) {
    override fun generateCondition(node: Node) = filter.generateCondition(node)
}
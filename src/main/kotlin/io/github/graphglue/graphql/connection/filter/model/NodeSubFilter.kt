package io.github.graphglue.graphql.connection.filter.model

import de.graphglue.graphql.connection.filter.definition.NodeSubFilterDefinition
import org.neo4j.cypherdsl.core.Node

class NodeSubFilter(definition: NodeSubFilterDefinition, val filter: Filter) : FilterEntry(definition) {
    override fun generateCondition(node: Node) = filter.generateCondition(node)
}
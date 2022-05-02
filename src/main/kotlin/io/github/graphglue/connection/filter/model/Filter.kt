package io.github.graphglue.connection.filter.model

import io.github.graphglue.data.execution.CypherConditionGenerator
import org.neo4j.cypherdsl.core.Node

/**
 * Parsed version of an overall filter
 *
 * @param nodeFilter the top level parsed filter
 */
data class Filter(val nodeFilter: NodeFilter) : CypherConditionGenerator {
    override fun generateCondition(node: Node) = nodeFilter.generateCondition(node)
}
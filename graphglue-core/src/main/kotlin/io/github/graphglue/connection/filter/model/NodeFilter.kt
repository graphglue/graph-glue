package io.github.graphglue.connection.filter.model

import io.github.graphglue.data.execution.CypherConditionGenerator
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Conditions
import org.neo4j.cypherdsl.core.Node

/**
 * Filter for a specific type of node
 * Filters instances of this type by applying a list of filter entries joined by AND
 *
 * @param entries the entries to join
 */
data class NodeFilter(val entries: List<CypherConditionGenerator> = emptyList()) : CypherConditionGenerator {
    override fun generateCondition(node: Node): Condition {
        return if (entries.isEmpty()) {
            Conditions.isTrue()
        } else {
            entries.fold(Conditions.noCondition()) { condition, entry ->
                condition.and(entry.generateCondition(node))
            }
        }
    }
}
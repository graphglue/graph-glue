package io.github.graphglue.connection.filter.model

import io.github.graphglue.data.execution.CypherConditionGenerator
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Conditions
import org.neo4j.cypherdsl.core.Node

/**
 * Aggregate filter which can be used to join [NodeFilter]s by AND, OR and NOT
 */
abstract class MetaFilter : CypherConditionGenerator

/**
 * [MetaFilter] which joins several [NodeFilter]s by AND
 *
 * @param subNodeFilters the list of filters to join
 */
data class AndMetaFilter(val subNodeFilters: List<NodeFilter>) : MetaFilter() {
    init {
        if (subNodeFilters.isEmpty()) {
            throw IllegalArgumentException("no sub-filters provided")
        }
    }

    override fun generateCondition(node: Node): Condition {
        return subNodeFilters.fold(Conditions.noCondition()) { condition, subNodeFilter ->
            condition.and(subNodeFilter.generateCondition(node))
        }
    }
}

/**
 * [MetaFilter] which joins several [NodeFilter]s by OR
 *
 * @param subNodeFilters the list of filters to join
 */
data class OrMetaFilter(val subNodeFilters: List<NodeFilter>) : MetaFilter() {
    init {
        if (subNodeFilters.isEmpty()) {
            throw IllegalArgumentException("no sub-filters provided")
        }
    }

    override fun generateCondition(node: Node): Condition {
        return subNodeFilters.fold(Conditions.noCondition()) { condition, subNodeFilter ->
            condition.or(subNodeFilter.generateCondition(node))
        }
    }
}

/**
 * [MetaFilter] which negates a single [NodeFilter]
 *
 * @param subNodeFilter the filter to negate
 */
data class NotMetaFilter(val subNodeFilter: NodeFilter) : MetaFilter() {
    override fun generateCondition(node: Node): Condition {
        return subNodeFilter.generateCondition(node).not()
    }
}

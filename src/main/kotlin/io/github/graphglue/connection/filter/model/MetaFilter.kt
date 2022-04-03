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
 * [MetaFilter] which joins several [MetaFilter]s by AND
 *
 * @param subMetaFilters the list of filters to join
 */
data class AndMetaFilter(val subMetaFilters: List<MetaFilter>) : MetaFilter() {
    init {
        if (subMetaFilters.isEmpty()) {
            throw IllegalArgumentException("no sub-filters provided")
        }
    }

    override fun generateCondition(node: Node): Condition {
        return subMetaFilters.fold(Conditions.noCondition()) { condition, subMetaFilter ->
            condition.and(subMetaFilter.generateCondition(node))
        }
    }
}

/**
 * [MetaFilter] which joins several [MetaFilter]s by OR
 *
 * @param subMetaFilters the list of filters to join
 */
data class OrMetaFilter(val subMetaFilters: List<MetaFilter>) : MetaFilter() {
    init {
        if (subMetaFilters.isEmpty()) {
            throw IllegalArgumentException("no sub-filters provided")
        }
    }

    override fun generateCondition(node: Node): Condition {
        return subMetaFilters.fold(Conditions.noCondition()) { condition, subMetaFilter ->
            condition.or(subMetaFilter.generateCondition(node))
        }
    }
}

/**
 * [MetaFilter] which negates a single [MetaFilter]
 *
 * @param subMetaFilter the filter to negate
 */
data class NotMetaFilter(val subMetaFilter: MetaFilter) : MetaFilter() {
    override fun generateCondition(node: Node): Condition {
        return subMetaFilter.generateCondition(node).not()
    }
}

/**
 * [MetaFilter] to wrap a [NodeFilter]
 *
 * @param nodeFilter the filter to wrap
 */
data class NodeMetaFilter(val nodeFilter: NodeFilter) : MetaFilter() {
    override fun generateCondition(node: Node): Condition {
        return nodeFilter.generateCondition(node)
    }
}
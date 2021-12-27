package com.nkcoding.graphglue.graphql.connection.filter.model

import com.nkcoding.graphglue.neo4j.CypherConditionGenerator
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Conditions
import org.neo4j.cypherdsl.core.Node

abstract class MetaFilter : CypherConditionGenerator

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

data class NotMetaFilter(val subMetaFilter: MetaFilter) : MetaFilter() {
    override fun generateCondition(node: Node): Condition {
        return subMetaFilter.generateCondition(node).not()
    }
}

data class NodeMetaFilter(val nodeFilter: NodeFilter) : MetaFilter() {
    override fun generateCondition(node: Node): Condition {
        return nodeFilter.generateCondition(node)
    }
}
package io.github.graphglue.connection.filter.model

import io.github.graphglue.connection.filter.definition.AndMetaFilterDefinition
import io.github.graphglue.connection.filter.definition.MetaFilterDefinition
import io.github.graphglue.connection.filter.definition.NotMetaFilterDefinition
import io.github.graphglue.connection.filter.definition.OrMetaFilterDefinition
import io.github.graphglue.data.execution.CypherConditionGenerator
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Cypher
import org.neo4j.cypherdsl.core.Node

/**
 * Aggregate filter which can be used to join [NodeFilter]s by AND, OR and NOT
 *
 * @param definition associated definition of the entry
 */
abstract class MetaFilter(definition: MetaFilterDefinition) : FilterEntry(definition)

/**
 * [MetaFilter] which joins several [NodeFilter]s by AND
 *
 * @param definition associated definition of the entry
 * @param subNodeFilters the list of filters to join
 */
class AndMetaFilter(definition: AndMetaFilterDefinition, val subNodeFilters: List<NodeFilter>) : MetaFilter(definition) {
    init {
        if (subNodeFilters.isEmpty()) {
            throw IllegalArgumentException("no sub-filters provided")
        }
    }

    override fun generateCondition(node: Node): Condition {
        return subNodeFilters.fold(Cypher.noCondition()) { condition, subNodeFilter ->
            condition.and(subNodeFilter.generateCondition(node))
        }
    }
}

/**
 * [MetaFilter] which joins several [NodeFilter]s by OR
 *
 * @param definition associated definition of the entry
 * @param subNodeFilters the list of filters to join
 */
class OrMetaFilter(definition: OrMetaFilterDefinition, val subNodeFilters: List<NodeFilter>) : MetaFilter(definition) {
    init {
        if (subNodeFilters.isEmpty()) {
            throw IllegalArgumentException("no sub-filters provided")
        }
    }

    override fun generateCondition(node: Node): Condition {
        return subNodeFilters.fold(Cypher.noCondition()) { condition, subNodeFilter ->
            condition.or(subNodeFilter.generateCondition(node))
        }
    }
}

/**
 * [MetaFilter] which negates a single [NodeFilter]
 *
 * @param definition associated definition of the entry
 * @param subNodeFilter the filter to negate
 */
class NotMetaFilter(definition: NotMetaFilterDefinition, val subNodeFilter: NodeFilter) : MetaFilter(definition) {
    override fun generateCondition(node: Node): Condition {
        return subNodeFilter.generateCondition(node).not()
    }
}

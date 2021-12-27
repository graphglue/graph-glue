package com.nkcoding.graphglue.graphql.connection.filter.model

import com.nkcoding.graphglue.neo4j.CypherConditionGenerator
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Conditions
import org.neo4j.cypherdsl.core.Node

data class NodeFilter(val entries: List<FilterEntry> = emptyList()) : CypherConditionGenerator {
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
package io.github.graphglue.graphql.connection.filter.model

import io.github.graphglue.db.CypherConditionGenerator
import org.neo4j.cypherdsl.core.Node

data class Filter(val metaFilter: MetaFilter) : CypherConditionGenerator {
    override fun generateCondition(node: Node) = metaFilter.generateCondition(node)
}
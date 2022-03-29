package io.github.graphglue.graphql.connection.filter.model

import de.graphglue.graphql.connection.filter.definition.SimpleFilterEntryDefinition
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Node

class SimpleFilterEntry<T>(private val simpleDefinition: SimpleFilterEntryDefinition<T>, val value: T) :
    FilterEntry(simpleDefinition) {
    override fun generateCondition(node: Node): Condition {
        return simpleDefinition.generateCondition(node, value)
    }
}
package io.github.graphglue.connection.filter.model

import io.github.graphglue.connection.filter.definition.SimpleFilterEntryDefinition
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Node

class SimpleFilterEntry<T>(private val simpleDefinition: SimpleFilterEntryDefinition<T>, val value: T) :
    FilterEntry(simpleDefinition) {
    override fun generateCondition(node: Node): Condition {
        return simpleDefinition.generateCondition(node, value)
    }
}
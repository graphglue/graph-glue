package io.github.graphglue.connection.filter.model

import io.github.graphglue.connection.filter.definition.SimpleFilterEntryDefinition
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Node

/**
 * Simple [FilterEntry] where the condition generation is only defined by the definition, and a provided value
 *
 * @param T type of the value used for the condition
 * @param simpleDefinition definition used to generate the condition
 */
class SimpleFilterEntry<T>(private val simpleDefinition: SimpleFilterEntryDefinition<T>, val value: T) :
    FilterEntry(simpleDefinition) {
    override fun generateCondition(node: Node): Condition {
        return simpleDefinition.generateCondition(node, value)
    }
}
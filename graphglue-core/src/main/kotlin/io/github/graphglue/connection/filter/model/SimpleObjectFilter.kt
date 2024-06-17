package io.github.graphglue.connection.filter.model

import io.github.graphglue.connection.filter.definition.FilterEntryDefinition
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Cypher
import org.neo4j.cypherdsl.core.Node

/**
 * [FilterEntry] which is defined by a [FilterEntryDefinition] and joins a list of entries by AND
 *
 * @param definition associated definition of the filter
 * @param entries filter entries joined by AND
 */
open class SimpleObjectFilter(definition: FilterEntryDefinition, val entries: List<FilterEntry>) :
    FilterEntry(definition) {
    override fun generateCondition(node: Node): Condition {
        return if (entries.isEmpty()) {
            Cypher.isTrue()
        } else {
            entries.fold(Cypher.noCondition()) { condition, entry ->
                condition.and(entry.generateCondition(node))
            }
        }
    }
}
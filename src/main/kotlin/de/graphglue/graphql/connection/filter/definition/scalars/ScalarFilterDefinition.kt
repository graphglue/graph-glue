package de.graphglue.graphql.connection.filter.definition.scalars

import de.graphglue.graphql.connection.filter.definition.FilterEntryDefinition
import de.graphglue.graphql.connection.filter.definition.SimpleObjectFilterDefinitionEntry
import de.graphglue.graphql.connection.filter.model.FilterEntry
import de.graphglue.graphql.connection.filter.model.SimpleObjectFilter
import graphql.schema.GraphQLInputType

abstract class ScalarFilterDefinition<T>(
    name: String,
    description: String,
    typeName: String,
    scalarType: GraphQLInputType,
    neo4jName: String,
    entries: List<ScalarFilterEntry<T>>
) : SimpleObjectFilterDefinitionEntry<FilterEntryDefinition>(
    name,
    description,
    typeName,
    (entries + getDefaultFilterEntries()).map {
        it.generateFilterEntry(scalarType, neo4jName)
    }
) {
    override fun parseEntry(value: Any?): FilterEntry {
        value as Map<*, *>
        val entries = value.map {
            val (name, entry) = it
            val definition = fields[name] ?: throw IllegalStateException("Unknown input")
            definition.parseEntry(entry)
        }
        return SimpleObjectFilter(this, entries)
    }
}

fun <T> getDefaultFilterEntries(): List<ScalarFilterEntryBase<T>> {
    return listOf(
        ScalarFilterEntry(
            "equals",
            "Matches values which are identical to the provided value"
        ) { property, value ->
            property.isEqualTo(value)
        },
        ScalarListFilterEntry(
            "in",
            "Matches values which are identical to any of the provided values"
        ) { property, value ->
            property.`in`(value)
        }
    )
}
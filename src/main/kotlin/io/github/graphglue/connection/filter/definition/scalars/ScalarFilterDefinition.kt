package io.github.graphglue.connection.filter.definition.scalars

import graphql.schema.GraphQLInputType
import io.github.graphglue.connection.filter.definition.FilterEntryDefinition
import io.github.graphglue.connection.filter.definition.SimpleObjectFilterDefinitionEntry
import io.github.graphglue.connection.filter.model.FilterEntry
import io.github.graphglue.connection.filter.model.SimpleObjectFilter

abstract class ScalarFilterDefinition<T>(
    name: String,
    description: String,
    typeName: String,
    scalarType: GraphQLInputType,
    neo4jName: String,
    entries: List<ScalarFilterEntryBase<T>>
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

private fun <T> getDefaultFilterEntries(): List<ScalarFilterEntryBase<T>> {
    return listOf(
        ScalarFilterEntry(
            "eq",
            "Matches values which are equal to the provided value"
        ) { property, value ->
            property.isEqualTo(value)
        },
        ScalarListFilterEntry(
            "in",
            "Matches values which are equal to any of the provided values"
        ) { property, value ->
            property.`in`(value)
        }
    )
}
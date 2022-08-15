package io.github.graphglue.connection.filter.definition.scalars

import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import io.github.graphglue.authorization.Permission
import io.github.graphglue.connection.filter.definition.FilterEntryDefinition
import io.github.graphglue.connection.filter.definition.SimpleObjectFilterDefinitionEntry
import io.github.graphglue.connection.filter.model.FilterEntry
import io.github.graphglue.connection.filter.model.SimpleObjectFilter

/**
 * Filter for a scalar property.
 * Defines a list of fields how the property can be filtered (e.g. eq, in, startsWith, ...).
 * If multiple fields are provided, these are joined by AND
 * Already defines `eq` and `in` entries
 *
 * @param name the name of the field on the [GraphQLInputObjectType]
 * @param description the description of the object type
 * @param typeName name of the constructed [GraphQLInputType] which serves as input for the filter
 * @param scalarType the [GraphQLInputType] for the field inputs (e.g. for eq, startsWith, ...)
 * @param neo4jName the name of the property of the node in the database (might be different from [name])
 * @param nullable if true, the scalar is nullable, otherwise it is non-nullable
 * @param entries additional fields of this filter, define how the property can be filtered (e.g. startsWith, ...)
 */
abstract class ScalarFilterDefinition(
    name: String,
    description: String,
    typeName: String,
    scalarType: GraphQLInputType,
    neo4jName: String,
    nullable: Boolean,
    entries: List<ScalarFilterEntryBase>,
) : SimpleObjectFilterDefinitionEntry<FilterEntryDefinition>(name,
    "Filter by $name",
    description,
    if (nullable) "Nullable${typeName}FilterInput" else "${typeName}FilterInput",
    (entries + getDefaultFilterEntries(nullable)).map {
        it.generateFilterEntry(scalarType, neo4jName)
    }) {
    override fun parseEntry(value: Any?, permission: Permission?): FilterEntry {
        value as Map<*, *>
        val entries = value.map { (name, entry) ->
            val definition = fields[name] ?: throw IllegalStateException("Unknown input")
            definition.parseEntry(entry, permission)
        }
        return SimpleObjectFilter(this, entries)
    }
}

/**
 * Provides a default filter entries: eq and in
 *
 * @param nullable if true, the field to filter for is nullable and therefore a isNull field should be generated
 * @return the list of generated filter entries
 */
private fun getDefaultFilterEntries(nullable: Boolean): List<ScalarFilterEntryBase> {
    val fields = mutableListOf(ScalarFilterEntry(
        "eq", "Matches values which are equal to the provided value"
    ) { property, value ->
        property.isEqualTo(value)
    }, ScalarListFilterEntry(
        "in", "Matches values which are equal to any of the provided values"
    ) { property, value ->
        property.`in`(value)
    })
    if (nullable) {
        fields.add(IsNullFilterEntry())
    }
    return fields
}
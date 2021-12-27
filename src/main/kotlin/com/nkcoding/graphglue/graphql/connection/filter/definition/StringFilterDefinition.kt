package com.nkcoding.graphglue.graphql.connection.filter.definition

import com.nkcoding.graphglue.graphql.connection.filter.model.FilterEntry
import com.nkcoding.graphglue.graphql.connection.filter.model.StringFilter
import com.nkcoding.graphglue.graphql.connection.filter.model.StringFilterEntry
import graphql.Scalars
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Expression
import org.neo4j.cypherdsl.core.Property

class StringFilterDefinition(name: String, neo4jName: String) :
    SimpleObjectFilterDefinitionEntry<StringFilterEntryDefinition>(
        name, "Filter which can be used to filter for Nodes with a specific String field", "StringFilterInput", listOf(
            StringFilterEntryDefinition(
                "equals",
                "Matches Strings which are identical to the provided value",
                neo4jName
            ) { property, value ->
                property.isEqualTo(value)
            },
            StringFilterEntryDefinition(
                "startsWith",
                "Matches Strings which start with the provided value",
                neo4jName
            ) { property, value ->
                property.startsWith(value)
            },
            StringFilterEntryDefinition(
                "endsWith",
                "Matches Strings which end with the provided value",
                neo4jName
            ) { property, value ->
                property.endsWith(value)
            },
            StringFilterEntryDefinition(
                "contains",
                "Matches Strings which contain the provided value",
                neo4jName
            ) { property, value ->
                property.contains(value)
            },
            StringFilterEntryDefinition(
                "matches",
                "Matches Strings using the provided RegEx",
                neo4jName
            ) { property, value ->
                property.matches(value)
            }
        )
    ) {
    override fun parseEntry(value: Any?): FilterEntry {
        value as Map<*, *>
        val entries = value.map {
            val (name, entry) = it
            val definition = fields[name] ?: throw IllegalStateException("Unknown input")
            definition.parseEntry(entry)
        }
        return StringFilter(this, entries)
    }
}

class StringFilterEntryDefinition(
    name: String,
    description: String,
    neo4jName: String,
    conditionGenerator: (property: Property, value: Expression) -> Condition
) :
    SimpleFilterEntryDefinition<String>(
        name,
        description,
        Scalars.GraphQLString,
        neo4jName,
        conditionGenerator
    ) {
    override fun parseEntry(value: Any?): StringFilterEntry {
        return StringFilterEntry(this, value as String)
    }
}
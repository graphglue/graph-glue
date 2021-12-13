package com.nkcoding.graphglue.graphql.connection.filter.definition

import com.nkcoding.graphglue.graphql.connection.filter.model.FilterEntry
import com.nkcoding.graphglue.graphql.connection.filter.model.StringFilterEntry
import graphql.Scalars

class StringFilterDefinition(name: String) : SimpleObjectFilterDefinitionEntry(
    name, "Filter which can be used to filter for Nodes with a specific String field", "StringFilterInput", listOf(
        StringFilterEntryDefinition("equals", "Matches Strings which are identical to the provided value"),
        StringFilterEntryDefinition("startsWith", "Matches Strings which start with the provided value"),
        StringFilterEntryDefinition("endsWith", "Matches Strings which end with the provided value"),
        StringFilterEntryDefinition("contains", "Matches Strings which contain the provided value"),
        StringFilterEntryDefinition("matches", "Matches Strings using the provided RegEx")
    )
) {
    override fun parseEntry(value: Any): FilterEntry {
        TODO()
    }
}

class StringFilterEntryDefinition(name: String, description: String) :
    SimpleFilterDefinitionEntry(name, description, Scalars.GraphQLString) {
    override fun parseEntry(value: Any): FilterEntry {
        return StringFilterEntry(this, value as String)
    }
}
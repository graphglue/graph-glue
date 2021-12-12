package com.nkcoding.graphglue.graphql.connection.filter.definition

import com.nkcoding.graphglue.graphql.connection.filter.model.FilterEntry
import com.nkcoding.graphglue.graphql.connection.filter.model.StringFilterEntry
import graphql.Scalars

class StringFilterDefinition(name: String) : SimpleObjectFilterDefinitionEntry(
    name, "StringFilterInput", listOf(
        StringFilterEntryDefinition("equals"),
        StringFilterEntryDefinition("startsWith"),
        StringFilterEntryDefinition("endsWith"),
        StringFilterEntryDefinition("contains"),
        StringFilterEntryDefinition("matches")
    )
) {
    override fun parseEntry(value: Any): FilterEntry {
        TODO()
    }
}

class StringFilterEntryDefinition(name: String) : SimpleFilterDefinitionEntry(name, Scalars.GraphQLString) {
    override fun parseEntry(value: Any): FilterEntry {
        return StringFilterEntry(this, value as String)
    }
}
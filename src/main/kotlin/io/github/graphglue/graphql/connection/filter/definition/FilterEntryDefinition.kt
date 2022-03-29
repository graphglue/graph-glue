package io.github.graphglue.graphql.connection.filter.definition

import io.github.graphglue.graphql.connection.filter.model.FilterEntry

abstract class FilterEntryDefinition(val name: String, val description: String) : GraphQLInputTypeGenerator {
    abstract fun parseEntry(value: Any?): FilterEntry
}
package io.github.graphglue.connection.filter.definition

import io.github.graphglue.connection.filter.model.FilterEntry

abstract class FilterEntryDefinition(val name: String, val description: String) : GraphQLInputTypeGenerator {
    abstract fun parseEntry(value: Any?): FilterEntry
}
package de.graphglue.graphql.connection.filter.definition

import de.graphglue.graphql.connection.filter.model.FilterEntry
import de.graphglue.graphql.generation.GraphQLInputTypeGenerator

abstract class FilterEntryDefinition(val name: String, val description: String) : GraphQLInputTypeGenerator {
    abstract fun parseEntry(value: Any?): FilterEntry
}
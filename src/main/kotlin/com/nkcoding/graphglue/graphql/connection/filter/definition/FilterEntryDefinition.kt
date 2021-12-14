package com.nkcoding.graphglue.graphql.connection.filter.definition

import com.nkcoding.graphglue.graphql.connection.filter.model.FilterEntry
import com.nkcoding.graphglue.graphql.generation.GraphQLInputTypeGenerator

abstract class FilterEntryDefinition(val name: String, val description: String) : GraphQLInputTypeGenerator {
    abstract fun parseEntry(value: Any?): FilterEntry
}
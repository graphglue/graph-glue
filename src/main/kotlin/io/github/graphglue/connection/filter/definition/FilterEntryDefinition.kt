package io.github.graphglue.connection.filter.definition

import graphql.schema.GraphQLInputObjectType
import io.github.graphglue.connection.filter.model.FilterEntry

/**
 * Definition for a filter entry.
 * Defines a function to parse entries
 *
 * @param name the name of the field on the [GraphQLInputObjectType]
 * @param description the description of the field
 */
abstract class FilterEntryDefinition(val name: String, val description: String) : GraphQLInputTypeGenerator {

    /**
     * Parses the entry of the provided filter
     *
     * @param value the value to parse
     * @return the parsed filter entry
     */
    abstract fun parseEntry(value: Any?): FilterEntry
}
package com.nkcoding.graphglue.graphql.connection.filter.model

import com.nkcoding.graphglue.graphql.connection.filter.definition.FilterEntryDefinition

abstract class SimpleObjectFilter(definition: FilterEntryDefinition, val entries: List<FilterEntry>) :
    FilterEntry(definition) {
}
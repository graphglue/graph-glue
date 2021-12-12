package com.nkcoding.graphglue.graphql.connection.filter.model

import com.nkcoding.graphglue.graphql.connection.filter.definition.FilterEntryDefinition

abstract class SimpleFilterEntry<T>(definition: FilterEntryDefinition, val value: T) : FilterEntry(definition)
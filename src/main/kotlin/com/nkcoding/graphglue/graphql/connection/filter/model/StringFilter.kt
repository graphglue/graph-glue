package com.nkcoding.graphglue.graphql.connection.filter.model

import com.nkcoding.graphglue.graphql.connection.filter.definition.StringFilterDefinition
import com.nkcoding.graphglue.graphql.connection.filter.definition.StringFilterEntryDefinition

class StringFilter(definition: StringFilterDefinition, entries: List<StringFilterEntry>) :
    SimpleObjectFilter(definition, entries)

class StringFilterEntry(definition: StringFilterEntryDefinition, value: String) :
    SimpleFilterEntry<String>(definition, value)
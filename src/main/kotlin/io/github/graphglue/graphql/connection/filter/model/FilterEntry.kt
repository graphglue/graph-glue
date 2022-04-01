package io.github.graphglue.graphql.connection.filter.model

import io.github.graphglue.db.CypherConditionGenerator
import io.github.graphglue.graphql.connection.filter.definition.FilterEntryDefinition

abstract class FilterEntry(val definition: FilterEntryDefinition) : CypherConditionGenerator
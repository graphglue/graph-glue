package io.github.graphglue.connection.filter.model

import io.github.graphglue.data.execution.CypherConditionGenerator
import io.github.graphglue.connection.filter.definition.FilterEntryDefinition

abstract class FilterEntry(val definition: FilterEntryDefinition) : CypherConditionGenerator
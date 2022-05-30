package io.github.graphglue.connection.filter.model

import io.github.graphglue.data.execution.CypherConditionGenerator
import io.github.graphglue.connection.filter.definition.FilterEntryDefinition

/**
 * Entry in a filter
 *
 * @param definition associated definition of the entry
 */
abstract class FilterEntry(val definition: FilterEntryDefinition) : CypherConditionGenerator
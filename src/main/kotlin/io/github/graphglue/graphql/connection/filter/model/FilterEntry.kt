package io.github.graphglue.graphql.connection.filter.model

import io.github.graphglue.graphql.connection.filter.definition.FilterEntryDefinition
import io.github.graphglue.neo4j.CypherConditionGenerator

abstract class FilterEntry(val definition: FilterEntryDefinition) : CypherConditionGenerator
package io.github.graphglue.graphql.connection.filter.model

import de.graphglue.graphql.connection.filter.definition.FilterEntryDefinition
import de.graphglue.neo4j.CypherConditionGenerator

abstract class FilterEntry(val definition: FilterEntryDefinition) : CypherConditionGenerator
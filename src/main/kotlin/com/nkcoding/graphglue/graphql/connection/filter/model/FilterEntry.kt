package com.nkcoding.graphglue.graphql.connection.filter.model

import com.nkcoding.graphglue.graphql.connection.filter.definition.FilterEntryDefinition
import com.nkcoding.graphglue.neo4j.CypherConditionGenerator

abstract class FilterEntry(val definition: FilterEntryDefinition) : CypherConditionGenerator
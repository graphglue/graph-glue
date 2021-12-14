package com.nkcoding.graphglue.graphql.connection.filter.model

import com.nkcoding.graphglue.neo4j.CypherConditionGenerator

abstract class MetaFilter : CypherConditionGenerator

data class AndMetaFilter(val subMetaFilters: List<MetaFilter>) : MetaFilter()

data class OrMetaFilter(val subMetaFilters: List<MetaFilter>) : MetaFilter()

data class NotMetaFilter(val subMetaFilter: MetaFilter) : MetaFilter()

data class NodeMetaFilter(val nodeFilter: NodeFilter) : MetaFilter()
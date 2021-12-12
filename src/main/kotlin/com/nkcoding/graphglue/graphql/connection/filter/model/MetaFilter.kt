package com.nkcoding.graphglue.graphql.connection.filter.model

import com.nkcoding.graphglue.neo4j.CypherConditionGenerator

abstract class MetaFilter : CypherConditionGenerator

abstract class ListMetaFilter(val subMetaFilters: List<MetaFilter>)

class AndMetaFilter(subMetaFilters: List<MetaFilter>) : ListMetaFilter(subMetaFilters)

class OrMetaFilter(subMetaFilters: List<MetaFilter>) : ListMetaFilter(subMetaFilters)

class NotMetaFilter(val subMetaFilter: MetaFilter) : MetaFilter()

class NodeMetaFilter(val nodeFilter: NodeFilter) : MetaFilter()
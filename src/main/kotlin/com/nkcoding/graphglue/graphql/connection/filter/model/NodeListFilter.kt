package com.nkcoding.graphglue.graphql.connection.filter.model

import com.nkcoding.graphglue.graphql.connection.filter.definition.FilterEntryDefinition
import com.nkcoding.graphglue.graphql.connection.filter.definition.NodeListFilterDefinition

class NodeListFilter(definition: NodeListFilterDefinition, entries: List<NodeListFilterEntry>) :
    SimpleObjectFilter(definition, entries) {
}

abstract class NodeListFilterEntry(definition: FilterEntryDefinition, filter: NodeSubFilter) : FilterEntry(definition)

class AllNodeListFilterEntry(definition: FilterEntryDefinition, filter: NodeSubFilter) : NodeListFilterEntry(definition, filter)

class SomeNodeListFilterEntry(definition: FilterEntryDefinition, filter: NodeSubFilter) : NodeListFilterEntry(definition, filter)

class NoneNodeListFilterEntry(definition: FilterEntryDefinition, filter: NodeSubFilter) : NodeListFilterEntry(definition, filter)
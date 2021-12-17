package com.nkcoding.graphglue.graphql.connection.filter.model

import com.nkcoding.graphglue.graphql.connection.filter.definition.NodeSubFilterDefinition

class NodeSubFilter(definition: NodeSubFilterDefinition, val filter: Filter) : FilterEntry(definition) {
}
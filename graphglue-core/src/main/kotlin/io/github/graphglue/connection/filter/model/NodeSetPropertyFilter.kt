package io.github.graphglue.connection.filter.model

import io.github.graphglue.connection.filter.definition.NodeSetPropertyFilterDefinition

/**
 * [SimpleObjectFilter] with a [NodeSetPropertyFilterDefinition] definition
 *
 * @param definition associated definition of the filter
 * @param entries filter entries joined by AND
 */
class NodeSetPropertyFilter(definition: NodeSetPropertyFilterDefinition, entries: List<NodeRelationshipFilterEntry>) :
    SimpleObjectFilter(definition, entries)
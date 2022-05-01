package io.github.graphglue.connection.filter.definition

import graphql.schema.GraphQLInputType
import io.github.graphglue.connection.filter.model.AnyNodeRelationshipFilterEntry
import io.github.graphglue.connection.filter.model.FilterEntry
import io.github.graphglue.util.CacheMap

/**
 * Definition for a filter used to filter for NodeProperties
 *
 * @param nodeSubFilterDefinition the filter which is wrapped to filter for the relationship
 */
class NodePropertyFilterDefinition(
    private val nodeSubFilterDefinition: NodeSubFilterDefinition
) : FilterEntryDefinition(nodeSubFilterDefinition.name, nodeSubFilterDefinition.description) {

    override fun parseEntry(value: Any?): FilterEntry {
        return AnyNodeRelationshipFilterEntry(nodeSubFilterDefinition, nodeSubFilterDefinition.parseEntry(value))
    }

    override fun toGraphQLType(inputTypeCache: CacheMap<String, GraphQLInputType>): GraphQLInputType {
        return nodeSubFilterDefinition.toGraphQLType(inputTypeCache)
    }
}
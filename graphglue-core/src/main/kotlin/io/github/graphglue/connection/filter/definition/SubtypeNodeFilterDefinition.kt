package io.github.graphglue.connection.filter.definition

import graphql.schema.GraphQLInputType
import io.github.graphglue.authorization.Permission
import io.github.graphglue.connection.filter.model.FilterEntry
import io.github.graphglue.connection.filter.model.SubtypeNodeFilter
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.model.Node
import io.github.graphglue.util.CacheMap

/**
 * Definition for a filter used to filter for a specific subtype (and potentially by attributes
 * of that subtype) of a node
 *
 * @param nodeDefinition the definition of the subtype
 * @param nodeFilterDefinition the definition of the filter for the subtype
 */
class SubtypeNodeFilterDefinition(
    val nodeDefinition: NodeDefinition,
    val nodeFilterDefinition: FilterDefinition<out Node>
) : FilterEntryDefinition("is${nodeDefinition.name}And", "Filter for nodes of type ${nodeDefinition.name}") {

    override fun parseEntry(value: Any?, permission: Permission?): FilterEntry {
        return SubtypeNodeFilter(
            this, nodeFilterDefinition.parseFilter(value, permission)
        )
    }

    override fun toGraphQLType(inputTypeCache: CacheMap<String, GraphQLInputType>): GraphQLInputType {
        return nodeFilterDefinition.toGraphQLType(inputTypeCache)
    }
}
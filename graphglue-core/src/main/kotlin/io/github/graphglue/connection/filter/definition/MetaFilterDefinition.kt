package io.github.graphglue.connection.filter.definition

import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import io.github.graphglue.authorization.Permission
import io.github.graphglue.connection.filter.model.AndMetaFilter
import io.github.graphglue.connection.filter.model.FilterEntry
import io.github.graphglue.connection.filter.model.NotMetaFilter
import io.github.graphglue.connection.filter.model.OrMetaFilter
import io.github.graphglue.model.Node
import io.github.graphglue.util.CacheMap

/**
 * Definition for a meta filter
 *
 * @param name the name of the field on the [GraphQLInputObjectType]
 * @param description the description of the field
 * @param filterDefinition the definition of the aggregated filter
 */
abstract class MetaFilterDefinition(
    name: String, description: String, val filterDefinition: FilterDefinition<out Node>
) : FilterEntryDefinition(name, description)

/**
 * Definition for an and meta filter
 * Joins several filters by AND
 *
 * @param filterDefinition definition for the filters joined by AND
 */
class AndMetaFilterDefinition(filterDefinition: FilterDefinition<out Node>) :
    MetaFilterDefinition("and", "Connects all subformulas via and", filterDefinition) {
    override fun parseEntry(
        value: Any?, permission: Permission?
    ): FilterEntry {
        return AndMetaFilter(this, (value as List<*>).map { filterDefinition.parseNodeFilter(it, permission) })
    }

    override fun toGraphQLType(inputTypeCache: CacheMap<String, GraphQLInputType>): GraphQLInputType {
        return GraphQLList(GraphQLNonNull(filterDefinition.toGraphQLType(inputTypeCache)))
    }
}

/**
 * Definition for an or meta filter
 * Joins several filters by OR
 *
 * @param filterDefinition definition for the filters joined by OR
 */
class OrMetaFilterDefinition(filterDefinition: FilterDefinition<out Node>) :
    MetaFilterDefinition("or", "Connects all subformulas via or", filterDefinition) {
    override fun parseEntry(
        value: Any?, permission: Permission?
    ): FilterEntry {
        return OrMetaFilter(this, (value as List<*>).map { filterDefinition.parseNodeFilter(it, permission) })
    }

    override fun toGraphQLType(inputTypeCache: CacheMap<String, GraphQLInputType>): GraphQLInputType {
        return GraphQLList(GraphQLNonNull(filterDefinition.toGraphQLType(inputTypeCache)))
    }
}

/**
 * Definition for a not meta filter
 * Negates a filter
 *
 * @param filterDefinition definition for the filter to negate
 */
class NotMetaFilterDefinition(filterDefinition: FilterDefinition<out Node>) :
    MetaFilterDefinition("not", "Negates the subformula", filterDefinition) {
    override fun parseEntry(
        value: Any?, permission: Permission?
    ): FilterEntry {
        return NotMetaFilter(this, filterDefinition.parseNodeFilter(value, permission))
    }

    override fun toGraphQLType(inputTypeCache: CacheMap<String, GraphQLInputType>): GraphQLInputType {
        return filterDefinition.toGraphQLType(inputTypeCache)
    }

}
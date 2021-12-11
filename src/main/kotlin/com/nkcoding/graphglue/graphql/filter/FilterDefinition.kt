package com.nkcoding.graphglue.graphql.filter

import com.nkcoding.graphglue.graphql.extensions.getSimpleName
import com.nkcoding.graphglue.graphql.generation.GraphQLInputTypeGenerator
import com.nkcoding.graphglue.graphql.generation.GraphQLTypeCache
import com.nkcoding.graphglue.model.Node
import graphql.schema.*
import kotlin.reflect.KClass

class FilterDefinition<T : Node>(val entryType: KClass<T>, val entries: List<FilterDefinitionEntry>) :
    GraphQLInputTypeGenerator {

    fun toFilter(): Filter<T> {
        TODO()
    }

    override fun toGraphQLType(
        objectTypeCache: GraphQLTypeCache<GraphQLInputObjectType>,
        codeRegistry: GraphQLCodeRegistry.Builder
    ): GraphQLInputType {
        val filterName = "${entryType.getSimpleName()}FilterInput"
        val nodeFilterName = "${entryType.getSimpleName()}NodeFilterInput"

        val nodeFilter = objectTypeCache.buildIfNotInCache(nodeFilterName) {
            val builder = GraphQLInputObjectType.newInputObject()
            builder.name(nodeFilterName)
            for (entry in entries) {
                builder.field {
                    it.name(entry.name).type(entry.toGraphQLType(objectTypeCache, codeRegistry))
                }
            }
            builder.build()
        }

        val subFilter = GraphQLTypeReference(filterName)
        val nonNullSubFilterList = GraphQLList(GraphQLNonNull(subFilter))

        return objectTypeCache.buildIfNotInCache(filterName) {
            GraphQLInputObjectType.newInputObject()
                .name(filterName)
                .field {
                    it.name("and").type(nonNullSubFilterList)
                }.field {
                    it.name("or").type(nonNullSubFilterList)
                }
                .field {
                    it.name("not").type(subFilter)
                }
                .field {
                    it.name("node").type(nodeFilter)
                }
                .build()
        }
    }
}
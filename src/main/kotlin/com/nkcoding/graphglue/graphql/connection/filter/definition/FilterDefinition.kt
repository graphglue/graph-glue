package com.nkcoding.graphglue.graphql.connection.filter.definition

import com.nkcoding.graphglue.graphql.connection.filter.model.Filter
import com.nkcoding.graphglue.graphql.extensions.getSimpleName
import com.nkcoding.graphglue.graphql.generation.GraphQLInputTypeGenerator
import com.nkcoding.graphglue.model.Node
import graphql.schema.*
import kotlin.reflect.KClass

class FilterDefinition<T : Node>(val entryType: KClass<T>, val entries: List<FilterEntryDefinition>) :
    GraphQLInputTypeGenerator {

    fun parseFilter(value: Any): Filter {
        TODO()
    }

    override fun toGraphQLType(
        objectTypeCache: MutableMap<String, GraphQLInputObjectType>
    ): GraphQLInputType {
        val filterName = "${entryType.getSimpleName()}FilterInput"
        val nodeFilterName = "${entryType.getSimpleName()}NodeFilterInput"

        val nodeFilter = objectTypeCache.computeIfAbsent(nodeFilterName) {
            val builder = GraphQLInputObjectType.newInputObject()
            builder.name(nodeFilterName)
            builder.description("Filter used to filter ${entryType.getSimpleName()}")
            for (entry in entries) {
                builder.field {
                    it.name(entry.name).description(entry.description).type(entry.toGraphQLType(objectTypeCache))
                }
            }
            builder.build()
        }

        val subFilter = GraphQLTypeReference(filterName)
        val nonNullSubFilterList = GraphQLList(GraphQLNonNull(subFilter))

        return objectTypeCache.computeIfAbsent(filterName) {
            GraphQLInputObjectType.newInputObject().name(filterName)
                .description("Used to build propositional formula consisting of ${nodeFilterName}. Exactly one if its fields has to be provided")
                .field {
                    it.name("and")
                        .description("Connects all subformulas via and")
                        .type(nonNullSubFilterList)
                }.field {
                    it.name("or")
                        .description("Connects all subformulas via or")
                        .type(nonNullSubFilterList)
                }.field {
                    it.name("not")
                        .description("Negates the subformula")
                        .type(subFilter)
                }.field {
                    it.name("node")
                        .description("Wrapper around $nodeFilterName")
                        .type(nodeFilter)
                }.build()
        }
    }
}
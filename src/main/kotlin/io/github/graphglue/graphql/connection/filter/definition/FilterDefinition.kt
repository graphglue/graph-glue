package io.github.graphglue.graphql.connection.filter.definition

import graphql.schema.*
import io.github.graphglue.graphql.connection.filter.model.*
import io.github.graphglue.graphql.extensions.getSimpleName
import io.github.graphglue.model.Node
import io.github.graphglue.util.CacheMap
import kotlin.reflect.KClass

class FilterDefinition<T : Node>(private val entryType: KClass<T>) :
    GraphQLInputTypeGenerator {

    private lateinit var entries: Map<String, FilterEntryDefinition>

    fun init(entries: List<FilterEntryDefinition>) {
        this.entries = entries.associateBy { it.name }
    }

    fun parseFilter(value: Any?): Filter {
        return Filter(parseMetaFilter(value))
    }

    private fun parseMetaFilter(value: Any?): MetaFilter {
        value as Map<*, *>
        if (value.size != 1) {
            throw IllegalArgumentException("Exactly one of the fields [node, and, or, not] must be provided")
        }
        val (name, entry) = value.entries.first()
        return when (name) {
            "node" -> NodeMetaFilter(parseNodeFilter(entry!!))
            "and" -> {
                entry as List<*>
                AndMetaFilter(entry.map(::parseMetaFilter))
            }
            "or" -> {
                entry as List<*>
                OrMetaFilter(entry.map(::parseMetaFilter))
            }
            "not" -> NotMetaFilter(parseMetaFilter(entry))
            else -> throw IllegalStateException("Illegal input value which does not match GraphQL type")
        }
    }

    private fun parseNodeFilter(value: Any): NodeFilter {
        value as Map<*, *>
        val entries = value.map {
            val (name, entry) = it
            val definition = entries[name] ?: throw IllegalStateException("Unknown input")
            definition.parseEntry(entry)
        }
        return NodeFilter(entries)
    }

    override fun toGraphQLType(
        inputTypeCache: CacheMap<String, GraphQLInputType>
    ): GraphQLInputType {
        val filterName = "${entryType.getSimpleName()}FilterInput"
        val nodeFilterName = "${entryType.getSimpleName()}NodeFilterInput"

        val nodeFilter = inputTypeCache.computeIfAbsent(nodeFilterName, GraphQLTypeReference(nodeFilterName)) {
            val builder = GraphQLInputObjectType.newInputObject()
            builder.name(nodeFilterName)
            builder.description("Filter used to filter ${entryType.getSimpleName()}")
            for (entry in entries.values) {
                builder.field {
                    it.name(entry.name).description(entry.description).type(entry.toGraphQLType(inputTypeCache))
                }
            }
            builder.build()
        }

        val subFilter = GraphQLTypeReference(filterName)
        val nonNullSubFilterList = GraphQLList(GraphQLNonNull(subFilter))

        return inputTypeCache.computeIfAbsent(filterName, GraphQLTypeReference(filterName)) {
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
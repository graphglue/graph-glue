package io.github.graphglue.connection.filter.definition

import graphql.schema.*
import io.github.graphglue.authorization.Permission
import io.github.graphglue.connection.filter.model.*
import io.github.graphglue.graphql.extensions.getSimpleName
import io.github.graphglue.model.Node
import io.github.graphglue.util.CacheMap
import kotlin.reflect.KClass

/**
 * Defines a filter for a specific [Node] type
 * Also handles parsing filters of that node type, including meta filters (filters joined by and, or, not)
 *
 * @param T the type of [Node]
 * @param entryType class of the type, used to have runtime type information
 */
class FilterDefinition<T : Node>(private val entryType: KClass<T>) :
    GraphQLInputTypeGenerator {

    /**
     * Entries of the filter (typically for fields of the type)
     * Joined by AND in the filter generation
     */
    private lateinit var entries: Map<String, FilterEntryDefinition>

    /**
     * Initializes [FilterDefinition.entries]
     * Used so that the [FilterDefinition] can be created before its entries are created,
     * allowing for cyclic filter definition.
     * Should be called only once
     *
     * @param entries value for [FilterDefinition.entries]
     */
    fun init(entries: List<FilterEntryDefinition>) {
        this.entries = entries.associateBy { it.name }
    }

    /**
     * Parses the GraphQL input into a filter
     *
     * @param value the input
     * @param permission the current read permission, used to only consider nodes in filters which match the permission
     * @return the parsed [Filter]
     */
    fun parseFilter(value: Any?, permission: Permission?): Filter {
        return Filter(parseMetaFilter(value, permission))
    }

    /**
     * Parses a meta filter.
     * Requires that value contains exactly one of node, and, or, not are provided.
     *
     * @param value the meta filter to parse
     * @param permission the current read permission, used to only consider nodes in filters which match the permission
     * @return the parsed meta filter
     * @throws IllegalStateException if the input type contains not one of node, and, or, not
     */
    private fun parseMetaFilter(value: Any?, permission: Permission?): MetaFilter {
        value as Map<*, *>
        if (value.size != 1) {
            throw IllegalArgumentException("Exactly one of the fields [node, and, or, not] must be provided")
        }
        val (name, entry) = value.entries.first()
        return when (name) {
            "node" -> NodeMetaFilter(parseNodeFilter(entry!!, permission))
            "and" -> {
                entry as List<*>
                AndMetaFilter(entry.map { parseMetaFilter(it, permission) })
            }
            "or" -> {
                entry as List<*>
                OrMetaFilter(entry.map { parseMetaFilter(it, permission) })
            }
            "not" -> NotMetaFilter(parseMetaFilter(entry, permission))
            else -> throw IllegalStateException("Illegal input value which does not match GraphQL type")
        }
    }

    /**
     * Parses a node filter
     * Requires that there is a definition present for all provided fields
     *
     * @param value the node filter to parse
     * @param permission the current read permission, used to only consider nodes in filters which match the permission
     * @return the parsed filter
     */
    private fun parseNodeFilter(value: Any, permission: Permission?): NodeFilter {
        value as Map<*, *>
        val entries = value.map {
            val (name, entry) = it
            val definition = entries[name] ?: throw IllegalStateException("Unknown input")
            definition.parseEntry(entry, permission)
        }
        return NodeFilter(entries)
    }

    override fun toGraphQLType(
        inputTypeCache: CacheMap<String, GraphQLInputType>
    ): GraphQLInputType {
        val filterName = "${entryType.getSimpleName()}FilterInput"
        val nodeFilterName = "${entryType.getSimpleName()}NodeFilterInput"

        val subFilter = GraphQLTypeReference(filterName)
        val nonNullSubFilterList = GraphQLList(GraphQLNonNull(subFilter))

        return inputTypeCache.computeIfAbsent(filterName, GraphQLTypeReference(filterName)) {
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
            createMetaFilterInputType(filterName, nodeFilterName, nonNullSubFilterList, subFilter, nodeFilter)
        }
    }

    /**
     * Creates the [GraphQLInputType] for a meta filter
     *
     * @param filterName the name of the meta filter
     * @param nodeFilterName the name of the node filter
     * @param nonNullSubFilterList [GraphQLInputType] for a list of non-null meta filters
     * @param subFilter [GraphQLInputType] for a non-null meta filter
     * @param nodeFilter [GraphQLInputType] for the node filter
     * @return the generated [GraphQLInputType] of the meta filter
     */
    private fun createMetaFilterInputType(
        filterName: String,
        nodeFilterName: String,
        nonNullSubFilterList: GraphQLInputType,
        subFilter: GraphQLInputType,
        nodeFilter: GraphQLInputType
    ): GraphQLInputType = GraphQLInputObjectType.newInputObject().name(filterName)
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
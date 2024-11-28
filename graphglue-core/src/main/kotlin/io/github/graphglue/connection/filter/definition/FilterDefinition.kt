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
        return Filter(parseNodeFilter(value, permission))
    }

    /**
     * Parses a node filter
     * Requires for each field that either
     * - a definition is present
     * - it is an and/or/not meta filter field
     *
     * @param value the node filter to parse
     * @param permission the current read permission, used to only consider nodes in filters which match the permission
     * @return the parsed filter
     */
    fun parseNodeFilter(value: Any?, permission: Permission?): NodeFilter {
        value as Map<*, *>
        val entries = value.map { (name, entry) ->
            val definition = entries[name] ?: throw IllegalStateException("Unknown input")
            definition.parseEntry(entry, permission)
        }
        return NodeFilter(entries)
    }

    override fun toGraphQLType(
        inputTypeCache: CacheMap<String, GraphQLInputType>
    ): GraphQLInputType {
        val filterName = "${entryType.getSimpleName()}FilterInput"

        val nodeFilter = GraphQLTypeReference(filterName)

        return inputTypeCache.computeIfAbsent(filterName, nodeFilter) {
            val builder = GraphQLInputObjectType.newInputObject()
            builder.name(filterName)
            builder.description("Filter used to filter ${entryType.getSimpleName()}")
            for (entry in entries.values) {
                builder.field {
                    it.name(entry.name).description(entry.description).type(entry.toGraphQLType(inputTypeCache))
                }
            }
            builder.build()
        }
    }

}
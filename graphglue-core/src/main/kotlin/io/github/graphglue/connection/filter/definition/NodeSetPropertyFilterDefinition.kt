package io.github.graphglue.connection.filter.definition

import graphql.schema.GraphQLInputObjectType
import io.github.graphglue.authorization.Permission
import io.github.graphglue.connection.filter.model.*
import io.github.graphglue.definition.RelationshipDefinition
import io.github.graphglue.graphql.extensions.getSimpleName
import io.github.graphglue.model.Node
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

/**
 * Definition for a set based filter used to filter for NodeSetProperties
 * Can be used for filters where either all, any or none of the elements of the list have to
 * match a filter
 *
 * @param name the name of the field on the [GraphQLInputObjectType]
 * @param nodeType the element type of the list, should be a subtype of [Node]
 * @param subFilterGenerator used to generate the filter for the `nodeType`
 * @param relationshipDefinition defines the relationship the property defines
 */
class NodeSetPropertyFilterDefinition(
    name: String,
    nodeType: KType,
    subFilterGenerator: SubFilterGenerator,
    relationshipDefinition: RelationshipDefinition
) : SimpleObjectFilterDefinitionEntry<NodeSubFilterDefinition>(
    name,
    "Filter by $name",
    "Used to filter by a connection-based property. Fields are joined by AND",
    "${nodeType.jvmErasure.getSimpleName()}ListFilterInput",
    listOf(
        NodeSubFilterDefinition(
            "all",
            "Filters for nodes where all of the related nodes match this filter",
            nodeType,
            subFilterGenerator,
            relationshipDefinition
        ), NodeSubFilterDefinition(
            "any",
            "Filters for nodes where any of the related nodes match this filter",
            nodeType,
            subFilterGenerator,
            relationshipDefinition
        ), NodeSubFilterDefinition(
            "none",
            "Filters for nodes where none of the related nodes match this filter",
            nodeType,
            subFilterGenerator,
            relationshipDefinition
        )
    )
) {

    override fun parseEntry(value: Any?, permission: Permission?): FilterEntry {
        value as Map<*, *>
        val entries = value.map { (name, entry) ->
            val definition = fields[name] ?: throw IllegalStateException("Unknown input")
            val filter = definition.parseEntry(entry, permission)
            when (name) {
                "all" -> AllNodeRelationshipFilterEntry(definition, filter, permission)
                "any" -> AnyNodeRelationshipFilterEntry(definition, filter, permission)
                "none" -> NoneNodeRelationshipFilterEntry(definition, filter, permission)
                else -> throw IllegalStateException("Unknown NodeListFilter entry")
            }
        }
        return NodeSetPropertyFilter(this, entries)
    }
}


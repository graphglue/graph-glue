package com.nkcoding.graphglue.graphql.connection.filter.definition

import com.nkcoding.graphglue.graphql.connection.filter.model.*
import com.nkcoding.graphglue.graphql.execution.definition.RelationshipDefinition
import com.nkcoding.graphglue.graphql.extensions.getSimpleName
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

class NodeListFilterDefinition(name: String, nodeType: KType, subFilterGenerator: SubFilterGenerator, relationshipDefinition: RelationshipDefinition) :
    SimpleObjectFilterDefinitionEntry<NodeSubFilterDefinition>(
        name, "", "${nodeType.jvmErasure.getSimpleName()}ListFilterInput",
        listOf(
            NodeSubFilterDefinition(
                "all",
                "Filters for nodes where all of the related nodes match this filter",
                nodeType,
                subFilterGenerator,
                relationshipDefinition
            ),
            NodeSubFilterDefinition(
                "any",
                "Filters for nodes where any of the related nodes match this filter",
                nodeType,
                subFilterGenerator,
                relationshipDefinition
            ),
            NodeSubFilterDefinition(
                "none",
                "Filters for nodes where none of the related nodes match this filter",
                nodeType,
                subFilterGenerator,
                relationshipDefinition
            )
        )
    ) {

    override fun parseEntry(value: Any?): FilterEntry {
        value as Map<*, *>
        val entries = value.map {
            val (name, entry) = it
            val definition = fields[name] ?: throw IllegalStateException("Unknown input")
            val filter = definition.parseEntry(entry)
            when(name) {
                "all" -> AllNodeListFilterEntry(definition, filter)
                "any" -> AnyNodeListFilterEntry(definition, filter)
                "none" -> NoneNodeListFilterEntry(definition, filter)
                else -> throw IllegalStateException("Unknown NodeListFilter entry")
            }
        }
        return NodeListFilter(this, entries)
    }
}


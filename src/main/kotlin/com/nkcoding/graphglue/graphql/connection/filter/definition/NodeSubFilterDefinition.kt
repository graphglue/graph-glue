package com.nkcoding.graphglue.graphql.connection.filter.definition

import com.nkcoding.graphglue.graphql.connection.filter.model.NodeSubFilter
import com.nkcoding.graphglue.model.Node
import graphql.schema.GraphQLInputType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

class NodeSubFilterDefinition(
    name: String,
    description: String,
    nodeType: KType,
    subFilterGenerator: SubFilterGenerator
) :
    FilterEntryDefinition(name, description) {

    @Suppress("UNCHECKED_CAST")
    private val subFilter = generateFilterDefinition(nodeType.jvmErasure as KClass<out Node>, subFilterGenerator)

    override fun parseEntry(value: Any?) = NodeSubFilter(this, subFilter.parseFilter(value))

    override fun toGraphQLType(objectTypeCache: MutableMap<String, GraphQLInputType>) =
        subFilter.toGraphQLType(objectTypeCache)
}
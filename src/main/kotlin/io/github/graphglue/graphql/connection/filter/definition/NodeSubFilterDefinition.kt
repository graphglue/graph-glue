package io.github.graphglue.graphql.connection.filter.definition

import io.github.graphglue.graphql.connection.filter.model.NodeSubFilter
import io.github.graphglue.model.Node
import io.github.graphglue.db.execution.definition.RelationshipDefinition
import io.github.graphglue.util.CacheMap
import graphql.schema.GraphQLInputType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

class NodeSubFilterDefinition(
    name: String,
    description: String,
    nodeType: KType,
    subFilterGenerator: SubFilterGenerator,
    val relationshipDefinition: RelationshipDefinition
) :
    FilterEntryDefinition(name, description) {

    @Suppress("UNCHECKED_CAST")
    private val subFilter = generateFilterDefinition(nodeType.jvmErasure as KClass<out Node>, subFilterGenerator)

    override fun parseEntry(value: Any?) = NodeSubFilter(this, subFilter.parseFilter(value))

    override fun toGraphQLType(inputTypeCache: CacheMap<String, GraphQLInputType>) =
        subFilter.toGraphQLType(inputTypeCache)
}
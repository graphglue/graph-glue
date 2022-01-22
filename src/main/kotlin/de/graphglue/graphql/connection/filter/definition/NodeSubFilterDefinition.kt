package de.graphglue.graphql.connection.filter.definition

import de.graphglue.graphql.connection.filter.model.NodeSubFilter
import de.graphglue.model.Node
import de.graphglue.neo4j.execution.definition.RelationshipDefinition
import de.graphglue.util.CacheMap
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
package io.github.graphglue.connection.filter.definition

import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import io.github.graphglue.connection.filter.model.NodeSubFilter
import io.github.graphglue.definition.RelationshipDefinition
import io.github.graphglue.model.Node
import io.github.graphglue.model.NodeRelationship
import io.github.graphglue.util.CacheMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

/**
 * [FilterEntryDefinition] wrapper for a [FilterDefinition].
 * Automatically generates the [FilterDefinition] base on the provided `nodeType`.
 * GraphQL type and parsing is handled by the generated [FilterDefinition]
 * Used in for filter entries based on [NodeRelationship] based properties.
 *
 * @param name the name of the field on the [GraphQLInputObjectType]
 * @param description the description of the field
 * @param nodeType defines which [FilterDefinition] to generate
 * @param subFilterGenerator used to generate the [FilterDefinition]
 * @param relationshipDefinition defines the relationship of the property
 */
class NodeSubFilterDefinition(
    name: String,
    description: String,
    nodeType: KType,
    subFilterGenerator: SubFilterGenerator,
    val relationshipDefinition: RelationshipDefinition
) : FilterEntryDefinition(name, description) {

    @Suppress("UNCHECKED_CAST")
    private val subFilter = generateFilterDefinition(nodeType.jvmErasure as KClass<out Node>, subFilterGenerator)

    override fun parseEntry(value: Any?) = NodeSubFilter(this, subFilter.parseFilter(value))

    override fun toGraphQLType(inputTypeCache: CacheMap<String, GraphQLInputType>) =
        subFilter.toGraphQLType(inputTypeCache)
}
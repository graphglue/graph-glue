package com.nkcoding.graphglue.graphql.execution.definition

import com.nkcoding.graphglue.graphql.extensions.getSimpleName
import com.nkcoding.graphglue.model.Node
import kotlin.reflect.KClass

class NodeDefinitionCollection(
    val backingCollection: Map<KClass<out Node>, NodeDefinition>,
    private val supertypeNodeDefinitionLookup: Map<Set<String>, NodeDefinition>
) {

    val definitionsByGraphQLName = backingCollection.mapKeys { it.key.getSimpleName() }

    inline fun <reified T : Node> getNodeDefinition(): NodeDefinition {
        return backingCollection[T::class]!!
    }

    fun getNodeDefinitionsFromGraphQLNames(names: List<String>): List<NodeDefinition> {
        return supertypeNodeDefinitionLookup[names.toSet()]?.let { listOf(it) }
            ?: names.map { definitionsByGraphQLName[it]!! }
    }
}
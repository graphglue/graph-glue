package com.nkcoding.graphglue.graphql.execution.definition

import com.nkcoding.graphglue.model.Node
import kotlin.reflect.KClass

interface NodeDefinitionCollection {
    val backingCollection: Map<KClass<out Node>, NodeDefinition>

    fun getNodeDefinitionsFromGraphQLNames(names: List<String>): List<NodeDefinition>
}

inline fun <reified T : Node> NodeDefinitionCollection.getNodeDefinition(): NodeDefinition {
    return backingCollection[T::class]!!
}
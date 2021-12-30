package com.nkcoding.graphglue.graphql.execution.definition

import com.nkcoding.graphglue.model.Node
import kotlin.reflect.KClass

interface NodeDefinitionCollection {
    fun getNodeDefinitionsFromGraphQLNames(names: List<String>): List<NodeDefinition>

    fun getNodeDefinition(nodeType: KClass<out Node>): NodeDefinition
}



inline fun <reified T : Node> NodeDefinitionCollection.getNodeDefinition(): NodeDefinition {
    return getNodeDefinition(T::class)
}
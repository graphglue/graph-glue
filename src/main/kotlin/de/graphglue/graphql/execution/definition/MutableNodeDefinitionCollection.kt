package de.graphglue.graphql.execution.definition

import de.graphglue.model.Node
import kotlin.reflect.KClass

interface MutableNodeDefinitionCollection : NodeDefinitionCollection {
    fun getOrCreate(kClass: KClass<out Node>): NodeDefinition
}
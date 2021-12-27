package com.nkcoding.graphglue.graphql.execution.definition

import com.nkcoding.graphglue.model.Node
import kotlin.reflect.KClass

interface MutableNodeDefinitionCollection : NodeDefinitionCollection {
    fun getOrCreate(kClass: KClass<out Node>): NodeDefinition
}
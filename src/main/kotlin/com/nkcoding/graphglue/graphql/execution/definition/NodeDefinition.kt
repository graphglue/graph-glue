package com.nkcoding.graphglue.graphql.execution.definition

import com.nkcoding.graphglue.graphql.extensions.getSimpleName
import com.nkcoding.graphglue.model.Node
import kotlin.reflect.KClass

class NodeDefinition(
    val nodeType: KClass<out Node>,
    oneRelationshipDefinitions: List<OneRelationshipDefinition>,
    manyRelationshipDefinitions: List<ManyRelationshipDefinition>
) {
    val oneRelationshipDefinitions = oneRelationshipDefinitions.associateBy { it.graphQLName }
    val manyRelationshipDefinitions = manyRelationshipDefinitions.associateBy { it.graphQLName }

    override fun toString() = nodeType.getSimpleName()
}
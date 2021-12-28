package com.nkcoding.graphglue.graphql.extensions

import com.expediagroup.graphql.generator.extensions.deepName
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinition
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinitionCollection
import graphql.schema.DataFetchingEnvironment

fun DataFetchingEnvironment.getParentNodeDefinition(nodeDefinitionCollection: NodeDefinitionCollection): NodeDefinition {
    val parentTypeName = parentType.deepName
    return nodeDefinitionCollection.getNodeDefinitionsFromGraphQLNames(listOf(parentTypeName)).first()
}
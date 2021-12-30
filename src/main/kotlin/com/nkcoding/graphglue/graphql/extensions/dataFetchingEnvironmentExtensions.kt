package com.nkcoding.graphglue.graphql.extensions

import com.expediagroup.graphql.generator.extensions.deepName
import com.nkcoding.graphglue.graphql.execution.NodeQuery
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinition
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinitionCollection
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment

fun DataFetchingEnvironment.getParentNodeDefinition(nodeDefinitionCollection: NodeDefinitionCollection): NodeDefinition {
    val parentTypeName = parentType.deepName
    return nodeDefinitionCollection.getNodeDefinitionsFromGraphQLNames(listOf(parentTypeName)).first()
}

fun <R> DataFetchingEnvironment.getDataFetcherResult(
    result: R,
    partId: String
): DataFetcherResult<R> {
    val nodeQuery = getLocalContext<NodeQuery>()
    return if (nodeQuery != null) {
        DataFetcherResult.newResult<R>()
            .data(result)
            .localContext(nodeQuery.parts[partId])
            .build()
    } else {
        DataFetcherResult.newResult<R>()
            .data(result)
            .build()
    }
}
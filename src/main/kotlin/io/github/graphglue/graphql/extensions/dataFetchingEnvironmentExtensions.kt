package io.github.graphglue.graphql.extensions

import com.expediagroup.graphql.generator.extensions.deepName
import io.github.graphglue.db.authorization.AuthorizationContext
import io.github.graphglue.db.execution.NodeQuery
import io.github.graphglue.db.execution.definition.NodeDefinition
import io.github.graphglue.db.execution.definition.NodeDefinitionCollection
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

val DataFetchingEnvironment.authorizationContext: AuthorizationContext?
    get() {
        return this.graphQlContext.get<AuthorizationContext?>(AuthorizationContext::class)
    }
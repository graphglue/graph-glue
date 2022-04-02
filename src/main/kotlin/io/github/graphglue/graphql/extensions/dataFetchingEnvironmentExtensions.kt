package io.github.graphglue.graphql.extensions

import com.expediagroup.graphql.generator.extensions.deepName
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import io.github.graphglue.authorization.AuthorizationContext
import io.github.graphglue.authorization.Permission
import io.github.graphglue.data.execution.NodeQuery
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.definition.NodeDefinitionCollection

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

/**
 * Gets the permission which is required for all data fetching
 * Can be provided under context key `Permission::class`
 */
val DataFetchingEnvironment.requiredPermission: Permission?
    get() {
        return this.graphQlContext.get<Permission?>(Permission::class)
    }

/**
 * Gets the authorization context necessary to generate new [Permission]s to check for permissions
 * Can either be set by setting the [requiredPermission] or can be provided under context key
 * `AuthorizationContext::class`
 */
val DataFetchingEnvironment.authorizationContext: AuthorizationContext?
    get() {
        return this.requiredPermission?.context
            ?: this.graphQlContext.get<AuthorizationContext>(AuthorizationContext::class)
    }
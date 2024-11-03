package io.github.graphglue.graphql.extensions

import com.expediagroup.graphql.generator.extensions.deepName
import graphql.schema.DataFetchingEnvironment
import io.github.graphglue.authorization.AuthorizationContext
import io.github.graphglue.authorization.Permission
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.definition.NodeDefinitionCollection

/**
 * Gets the [NodeDefinition] associated with the parent type
 * Uses [NodeDefinitionCollection.getNodeDefinitionsFromGraphQLNames] to find the correct [NodeDefinition]
 *
 * @param nodeDefinitionCollection collection of all known  [NodeDefinition]s
 * @return the found [NodeDefinition]
 */
fun DataFetchingEnvironment.getParentNodeDefinition(nodeDefinitionCollection: NodeDefinitionCollection): NodeDefinition {
    val parentTypeName = parentType.deepName
    return nodeDefinitionCollection.getNodeDefinitionsFromGraphQLNames(listOf(parentTypeName)).first()
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
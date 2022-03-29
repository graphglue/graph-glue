package io.github.graphglue.neo4j.authorization

import io.github.graphglue.model.Authorization

/**
 * Context for authorization
 *
 * @param name name of the authorization, must match [Authorization.name]
 * @param context map of parameters for condition generation
 */
data class AuthorizationContext(val name: String, val context: Map<String, Any>)
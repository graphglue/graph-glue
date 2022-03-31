package io.github.graphglue.db.authorization

import io.github.graphglue.model.Authorization

/**
 * Context for authorization
 *
 * @param name name of the authorization, must match [Authorization.name]
 * @param context context for condition generation
 */
data class Permission(val name: String, val context: AuthorizationContext)
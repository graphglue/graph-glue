package de.graphglue.neo4j.authorization

import de.graphglue.model.Authorization

/**
 * Context for authorization
 *
 * @property name name of the authorization, must match [Authorization.name]
 * @property context map of parameters for condition generation
 */
data class AuthorizationContext(val name: String, val context: Map<String, Any>)
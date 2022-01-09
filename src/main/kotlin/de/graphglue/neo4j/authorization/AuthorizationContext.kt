package de.graphglue.neo4j.authorization

data class AuthorizationContext(val name: String, val context: Map<String, Any>)
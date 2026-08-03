package io.github.graphglue.model

import io.github.graphglue.model.property.NodePropertyDelegate

/**
 * Annotation to mark [NodePropertyDelegate]s in GraphQL as nullable, even if the Kotlin Type is non-nullable.
 * Necessary, as authorization may lead to a Node not always being provided if the user has no permission
 * to read the node.
 */
@Target(AnnotationTarget.PROPERTY)
@MustBeDocumented
annotation class GraphQLNullable

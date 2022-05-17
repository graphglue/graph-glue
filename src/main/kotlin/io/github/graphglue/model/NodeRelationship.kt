package io.github.graphglue.model


/**
 * Used to mark manually handled node relationships
 * Must only be used on delegated properties with a delegate of type [NodePropertyDelegate] or [NodeSetPropertyDelegate]
 *
 * @param type the associated relationship type in the database
 * @param direction the direction of the relationship
 */
@MustBeDocumented
@Target(AnnotationTarget.PROPERTY)
annotation class NodeRelationship(val type: String, val direction: Direction)

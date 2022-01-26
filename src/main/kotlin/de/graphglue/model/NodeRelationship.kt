package de.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLDirective

const val NODE_RELATIONSHIP_DIRECTIVE = "nodeRelationship"

/**
 * Used to mark manually handled node relationships
 * Must only be used on delegated properties with a delegate of type [NodeProperty] or [NodeSetProperty]
 *
 * @param type the associated relationship type in the database
 * @param direction the direction of the relationship
 */
@MustBeDocumented
@Target(AnnotationTarget.PROPERTY)
@GraphQLDirective(NODE_RELATIONSHIP_DIRECTIVE)
annotation class NodeRelationship(val type: String, val direction: Direction)

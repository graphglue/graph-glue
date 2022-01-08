package de.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLDirective

const val NODE_RELATIONSHIP_DIRECTIVE = "nodeRelationship"

/**
 * Used to mark manually handled
 */
@MustBeDocumented
@Target(AnnotationTarget.PROPERTY)
@GraphQLDirective(NODE_RELATIONSHIP_DIRECTIVE)
annotation class NodeRelationship(val type: String, val direction: Direction)

package com.nkcoding.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLDirective
import org.springframework.data.neo4j.core.schema.Relationship

const val NODE_RELATIONSHIP_DIRECTIVE = "nodeRelationship"

/**
 * Used to mark manually handled
 */
@MustBeDocumented
@Target(AnnotationTarget.PROPERTY)
@GraphQLDirective(NODE_RELATIONSHIP_DIRECTIVE)
annotation class NodeRelationship(val type: String, val direction: Relationship.Direction)

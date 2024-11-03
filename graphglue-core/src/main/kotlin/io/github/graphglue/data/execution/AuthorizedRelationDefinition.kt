package io.github.graphglue.data.execution

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.definition.RelationshipDefinition

/**
 * A RelationshipDefinition with an optional authorization condition
 *
 * @param relationshipDefinition the relationship definition
 * @param relatedNodeDefinition the related node definition
 * @param authorizationCondition the authorization condition
 */
class AuthorizedRelationDefinition(
    val relationshipDefinition: RelationshipDefinition,
    val relatedNodeDefinition: NodeDefinition,
    val authorizationCondition: CypherConditionGenerator?
)
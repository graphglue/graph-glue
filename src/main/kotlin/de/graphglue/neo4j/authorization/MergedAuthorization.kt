package de.graphglue.neo4j.authorization

import de.graphglue.model.Authorization
import de.graphglue.model.Rule
import de.graphglue.neo4j.execution.definition.RelationshipDefinition

/**
 * A merged authorization composed of any amount of [Authorization]s
 *
 * @property name see [Authorization.name]
 * @property allow see [Authorization.allow]
 * @property allowFromRelated see [Authorization.allowFromRelated]
 * @property disallow see [Authorization.disallow]
 */
data class MergedAuthorization(
    val name: String,
    val allow: Set<Rule>,
    val allowFromRelated: Set<RelationshipDefinition>,
    val disallow: Set<Rule>
)
package de.graphglue.neo4j.authorization

import de.graphglue.model.Rule
import de.graphglue.neo4j.execution.definition.RelationshipDefinition

data class MergedAuthorization(
    val name: String,
    val allow: Set<Rule>,
    val allowFromRelated: Set<RelationshipDefinition>,
    val disallow: Set<Rule>
)
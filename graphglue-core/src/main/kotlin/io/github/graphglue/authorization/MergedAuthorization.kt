package io.github.graphglue.authorization

import io.github.graphglue.model.Authorization
import io.github.graphglue.model.Rule

/**
 * A merged authorization composed of any amount of [Authorization]s
 *
 * @param name see [Authorization.name]
 * @param allow see [Authorization.allow]
 * @param allowFromRelated see [Authorization.allowFromRelated]
 * @param disallow see [Authorization.disallow]
 * @param allowAll see [Authorization.allowAll]
 */
data class MergedAuthorization(
    val name: String,
    val allow: Set<Rule>,
    val allowFromRelated: Set<String>,
    val disallow: Set<Rule>,
    val allowAll: Boolean
)
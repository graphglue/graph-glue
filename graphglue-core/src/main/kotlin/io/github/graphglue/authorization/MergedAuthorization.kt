package io.github.graphglue.authorization

import io.github.graphglue.model.Rule

data class MergedAuthorization(
    val name: String,
    val allow: Set<Rule>,
    val allowFromRelated: Set<String>,
    val disallow: Set<Rule>,
    val allowAll: Boolean
)
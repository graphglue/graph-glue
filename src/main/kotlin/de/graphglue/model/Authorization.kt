package de.graphglue.model

annotation class Authorization(
    val name: String,
    val allow: Array<Rule>,
    val allowRelated: Array<NodeRelationship>,
    val disallow: Array<Rule>
)

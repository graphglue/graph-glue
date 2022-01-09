package de.graphglue.model

@Target(AnnotationTarget.CLASS)
@MustBeDocumented
@Repeatable
annotation class Authorization(
    val name: String,
    val allow: Array<Rule> = [],
    val allowFromRelated: Array<String> = [],
    val disallow: Array<Rule>  = []
)

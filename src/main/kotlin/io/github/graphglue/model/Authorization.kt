package io.github.graphglue.model

/**
 * Defines an authorization permission for a [Node]
 * To get the authorization definition for a specific [Node], the annotations on the class and all
 * subclasses are merged.
 *
 * @param name defines which authorization permission is defined (e.g. READ)
 * @param allow rules which allow access, access is only granted if any of the rules evaluates to `true`.
 *              Works in combination with `allowFromRelated`
 * @param allowFromRelated allow access can also be inherited from related nodes, must be equal to a relation property
 *                         name. Works in combination with `allow`
 * @param disallow rules which disallow access, if any rule evaluates to `true`, access is not granted (independent of
 *                 `allow` and `allowFromRelated`)
 */
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
@Repeatable
@Retention(AnnotationRetention.RUNTIME)
annotation class Authorization(
    val name: String,
    val allow: Array<Rule> = [],
    val allowFromRelated: Array<String> = [],
    val disallow: Array<Rule> = []
)

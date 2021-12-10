package com.nkcoding.graphglue.graphql.redirect

import com.expediagroup.graphql.generator.annotations.GraphQLDirective

/**
 * Marks the function with is executed if a property is redirected.
 * See [RedirectPropertyClass].
 * This has only an effect on public instance functions.
 */
@Target(AnnotationTarget.FUNCTION)
@GraphQLDirective(REDIRECT_DIRECTIVE_NAME)
annotation class RedirectPropertyFunction

package com.nkcoding.graphglue.graphql.redirect

import com.expediagroup.graphql.generator.annotations.GraphQLDirective

/**
 * A class with this annotation can be used as redirect property.
 * A class annotated with this annotation must have exactly one public function
 * annotated with [RedirectPropertyFunction].
 * If a Property has an instance of a class with this annotation as value,
 * if a graphql request is invoked, it uses the marked function to resolve the request.
 * This can for example be used to implement lists with pagination.
 */
@Target(AnnotationTarget.CLASS)
@GraphQLDirective(REDIRECT_DIRECTIVE_NAME)
annotation class RedirectPropertyClass
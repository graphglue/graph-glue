package de.graphglue.graphql.redirect

import graphql.introspection.Introspection
import graphql.schema.GraphQLDirective

/**
 * Name of the redirect directive
 */
const val REDIRECT_DIRECTIVE_NAME = "redirect"

/**
 * Alternative name for programmatically generated directive
 */
internal const val REDIRECT_DIRECTIVE_NAME_ALTERNATIVE = "redirect2"

/**
 * Directive that can be used for redirection and be manually applied
 */
val REDIRECT_PROPERTY_DIRECTIVE: GraphQLDirective = GraphQLDirective.newDirective()
    .name(REDIRECT_DIRECTIVE_NAME)
    .validLocation(Introspection.DirectiveLocation.OBJECT)
    .validLocation(Introspection.DirectiveLocation.FIELD)
    .build()
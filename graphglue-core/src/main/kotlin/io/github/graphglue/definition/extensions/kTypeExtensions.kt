package io.github.graphglue.definition.extensions

import kotlin.reflect.KType

/**
 * Helper to get the type of the first type argument
 */
val KType.firstTypeArgument get() = arguments.first().type!!
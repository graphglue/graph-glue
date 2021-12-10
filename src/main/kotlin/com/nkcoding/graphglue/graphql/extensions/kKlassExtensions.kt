package com.nkcoding.graphglue.graphql.extensions

import com.nkcoding.graphglue.graphql.redirect.RedirectPropertyFunction
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions

/**
 * Gets the redirect function of a class
 * See [RedirectPropertyFunction]
 */
internal val KClass<*>.redirectionFunction: KFunction<*>
    get() {
        val redirectFunctions = this.memberFunctions.filter { it.hasAnnotation<RedirectPropertyFunction>() }
        if (redirectFunctions.size != 1) {
            throw IllegalArgumentException("Provided type $this has not exactly one method annotated with RedirectPropertyFunction")
        }
        return redirectFunctions.first()
    }
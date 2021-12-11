package com.nkcoding.graphglue.graphql.extensions

import com.expediagroup.graphql.generator.annotations.GraphQLName
import com.expediagroup.graphql.generator.exceptions.CouldNotGetNameOfKClassException
import com.nkcoding.graphglue.graphql.redirect.RedirectPropertyFunction
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions

const val INPUT_SUFFIX = "Input"

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

/**
 * Taken from graphql-kotlin
 */
@Throws(CouldNotGetNameOfKClassException::class)
internal fun KClass<*>.getSimpleName(isInputClass: Boolean = false): String {
    val name = this.findAnnotation<GraphQLName>()?.value
        ?: this.simpleName
        ?: throw CouldNotGetNameOfKClassException(this)

    return when {
        isInputClass -> if (name.endsWith(INPUT_SUFFIX, true)) name else "$name$INPUT_SUFFIX"
        else -> name
    }
}
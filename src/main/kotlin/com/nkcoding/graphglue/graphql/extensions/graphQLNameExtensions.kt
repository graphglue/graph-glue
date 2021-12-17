package com.nkcoding.graphglue.graphql.extensions

import com.expediagroup.graphql.generator.annotations.GraphQLName
import com.expediagroup.graphql.generator.exceptions.CouldNotGetNameOfKClassException
import com.expediagroup.graphql.generator.exceptions.CouldNotGetNameOfKParameterException
import java.lang.reflect.Field
import kotlin.reflect.*
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.findParameterByName
import kotlin.reflect.full.primaryConstructor

const val INPUT_SUFFIX = "Input"

/**
 * Taken from graphql-kotlin
 */
@Throws(CouldNotGetNameOfKClassException::class)
fun KClass<*>.getSimpleName(isInputClass: Boolean = false): String {
    val name = this.getGraphQLName() ?: this.simpleName ?: throw CouldNotGetNameOfKClassException(this)

    return when {
        isInputClass -> if (name.endsWith(INPUT_SUFFIX, true)) name else "$name$INPUT_SUFFIX"
        else -> name
    }
}

/**
 * Taken from graphql-kotlin
 */
fun Field.getGraphQLName(): String = this.getAnnotation(GraphQLName::class.java)?.value ?: this.name

/**
 * Taken from graphql-kotlin
 */
fun KAnnotatedElement.getGraphQLName(): String? = this.findAnnotation<GraphQLName>()?.value

/**
 * Taken from graphql-kotlin
 */
fun KCallable<*>.getFunctionName(): String = this.getGraphQLName() ?: this.name

/**
 * Taken from graphql-kotlin
 */
@Throws(CouldNotGetNameOfKParameterException::class)
fun KParameter.getName(): String =
    this.getGraphQLName() ?: this.name ?: throw CouldNotGetNameOfKParameterException(this)

/**
 * Taken from graphql-kotlin
 */
fun KProperty<*>.getPropertyName(parentClass: KClass<*>): String =
    this.getGraphQLName() ?: getConstructorParameter(parentClass)?.getGraphQLName() ?: this.name

/**
 * Taken from graphql-kotlin
 */
private fun KProperty<*>.getConstructorParameter(parentClass: KClass<*>) =
    parentClass.findConstructorParameter(this.name)

/**
 * Taken from graphql-kotlin
 */
private fun KClass<*>.findConstructorParameter(name: String): KParameter? =
    this.primaryConstructor?.findParameterByName(name)

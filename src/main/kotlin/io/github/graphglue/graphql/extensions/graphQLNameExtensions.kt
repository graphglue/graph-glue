/*
 * Copyright 2019 Expedia, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * THIS IS AN MODIFIED VERSION
 */

package io.github.graphglue.graphql.extensions

import com.expediagroup.graphql.generator.annotations.GraphQLName
import com.expediagroup.graphql.generator.exceptions.CouldNotGetNameOfKClassException
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.findParameterByName
import kotlin.reflect.full.primaryConstructor

/**
 * Taken from graphql-kotlin
 * [https://github.com/ExpediaGroup/graphql-kotlin/blob/master/generator/graphql-kotlin-schema-generator/src/main/kotlin/com/expediagroup/graphql/generator/internal/extensions/kClassExtensions.kt]
 */
private const val INPUT_SUFFIX = "Input"

/**
 * Taken from graphql-kotlin
 * [https://github.com/ExpediaGroup/graphql-kotlin/blob/master/generator/graphql-kotlin-schema-generator/src/main/kotlin/com/expediagroup/graphql/generator/internal/extensions/kClassExtensions.kt]
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
 * [https://github.com/ExpediaGroup/graphql-kotlin/blob/master/generator/graphql-kotlin-schema-generator/src/main/kotlin/com/expediagroup/graphql/generator/internal/extensions/annotationExtensions.kt]
 */
fun KAnnotatedElement.getGraphQLName(): String? = this.findAnnotation<GraphQLName>()?.value

/**
 * Taken from graphql-kotlin
 * [https://github.com/ExpediaGroup/graphql-kotlin/blob/master/generator/graphql-kotlin-schema-generator/src/main/kotlin/com/expediagroup/graphql/generator/internal/extensions/kPropertyExtensions.kt]
 */
fun KProperty<*>.getPropertyName(parentClass: KClass<*>): String =
    this.getGraphQLName() ?: getConstructorParameter(parentClass)?.getGraphQLName() ?: this.name

/**
 * Taken from graphql-kotlin
 * [https://github.com/ExpediaGroup/graphql-kotlin/blob/master/generator/graphql-kotlin-schema-generator/src/main/kotlin/com/expediagroup/graphql/generator/internal/extensions/kPropertyExtensions.kt]
 */
private fun KProperty<*>.getConstructorParameter(parentClass: KClass<*>) =
    parentClass.findConstructorParameter(this.name)

/**
 * Taken from graphql-kotlin
 * [https://github.com/ExpediaGroup/graphql-kotlin/blob/master/generator/graphql-kotlin-schema-generator/src/main/kotlin/com/expediagroup/graphql/generator/internal/extensions/kClassExtensions.kt]
 */
private fun KClass<*>.findConstructorParameter(name: String): KParameter? =
    this.primaryConstructor?.findParameterByName(name)

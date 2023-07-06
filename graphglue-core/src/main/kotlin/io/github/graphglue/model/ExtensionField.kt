package io.github.graphglue.model

import io.github.graphglue.definition.ExtensionFieldDefinition
import java.lang.annotation.Inherited

/**
 * Annotation to define an additional field for a specific [Node] (and all subclasses)
 * The field is implemented by a bean with the defined name of type [ExtensionFieldDefinition]
 *
 * @param beanName the name of the bean defining the extension field, must be of type [ExtensionFieldDefinition]
 */
@Repeatable
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
@Inherited
@Retention(AnnotationRetention.RUNTIME)
annotation class ExtensionField(val beanName: String)

package io.github.graphglue.model

import io.github.graphglue.connection.filter.definition.FilterEntryDefinition
import java.lang.annotation.Inherited

/**
 * Annotation to define an additional filter property for a specific [Node] (and all subclasses)
 * The filter is implemented by a bean with the defined name of type [FilterEntryDefinition]
 *
 * @param beanName the name of the bean defining the filter, must be of type [FilterEntryDefinition]
 */
@Repeatable
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
@Inherited
@Retention(AnnotationRetention.RUNTIME)
annotation class AdditionalFilter(val beanName: String)

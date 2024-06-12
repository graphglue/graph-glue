package io.github.graphglue.model

import io.github.graphglue.connection.order.OrderPart
import java.lang.annotation.Inherited

/**
 * Annotation to define an additional order property for a specific [Node] (and all subclasses)
 * The filter is implemented by a bean with the defined name of type [OrderPart]
 *
 * @param beanName the name of the bean defining the order, must be of type [OrderPart]
 */
@Repeatable
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
@Inherited
@Retention(AnnotationRetention.RUNTIME)
annotation class AdditionalOrder(val beanName: String)
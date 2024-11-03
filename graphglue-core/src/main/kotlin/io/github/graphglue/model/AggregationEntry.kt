package io.github.graphglue.model

import kotlin.reflect.KClass

/**
 * Path entry for [AggregatedNodeRelationship]
 *
 * @param property the property on the current node to use
 * @param subclass optional subclass of the current class, if the selected property only is available on some subclasses
 */
annotation class AggregationEntry(val property: String, val subclass: KClass<out Node> = Node::class)
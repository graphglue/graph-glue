package io.github.graphglue.model

/**
 * Annotation to mark properties as order properties, meaning that nodes of the containing class can be ordered
 * by this property. Necessary that the property is a scalar property and of an in Neo4j orderable type
 * (e.g. String, Int, Double, ...)
 */
@MustBeDocumented
@Target(AnnotationTarget.PROPERTY)
annotation class OrderProperty

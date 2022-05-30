package io.github.graphglue.model

import io.github.graphglue.connection.filter.TypeFilterDefinitionEntry

/**
 * Annotation to mark properties as filter property, meaning that the filter for the class
 * containing the property gets a field added defined by the property.
 * Supported for Node(Set)Property backed properties, and scalar properties.
 * Other properties can be supported by providing a [TypeFilterDefinitionEntry] for the type of property.
 */
@Target(AnnotationTarget.PROPERTY)
@MustBeDocumented
annotation class FilterProperty

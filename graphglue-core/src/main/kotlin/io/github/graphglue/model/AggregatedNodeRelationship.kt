package io.github.graphglue.model

import java.lang.annotation.Inherited

/**
 * Declares an aggregated relationship for a [Node]
 *
 * @param name the name of the GraphQL field
 * @param description the description of the GraphQL field
 * @param property the property on the current node which represents the first segment of the aggregation
 * @param path all further steps of the aggregation in order, must not be empty
 */
@Repeatable
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
@Inherited
@Retention(AnnotationRetention.RUNTIME)
annotation class AggregatedNodeRelationship(val name: String, val description: String, val property: String, val path: Array<AggregationEntry>)
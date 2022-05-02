package io.github.graphglue.connection.filter.definition.scalars

import graphql.Scalars
import graphql.schema.GraphQLInputType
import io.github.graphglue.connection.filter.definition.FilterEntryDefinition
import io.github.graphglue.connection.filter.definition.SimpleFilterEntryDefinition

/**
 * [ScalarFilterEntryBase] which takes a boolean as input and checks if the scalar is null
 */
class IsNullFilterEntry : ScalarFilterEntryBase(
    "isNull",
    "If true, matches only null values, if false, matches only non-null values",
    { property, value ->
        val rawValue = value.value as Boolean
        if (rawValue) {
            property.isNull
        } else {
            property.isNotNull
        }
    }) {
    override fun generateFilterEntry(scalarType: GraphQLInputType, neo4jName: String): FilterEntryDefinition {
        return SimpleFilterEntryDefinition(
            name, description, Scalars.GraphQLBoolean, neo4jName, conditionGenerator
        )
    }
}
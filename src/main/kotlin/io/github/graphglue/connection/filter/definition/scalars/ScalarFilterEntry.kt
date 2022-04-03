package io.github.graphglue.connection.filter.definition.scalars

import graphql.schema.GraphQLInputType
import io.github.graphglue.connection.filter.definition.FilterEntryDefinition
import io.github.graphglue.connection.filter.definition.SimpleFilterEntryDefinition
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Expression
import org.neo4j.cypherdsl.core.Property

/**
 * [ScalarFilterEntryBase] which takes a single scalar as input
 * Can be used e.g. for a `eq` filter
 *
 * @param name the name of the field in the filter
 * @param description the description of the field
 * @param conditionGenerator used to generate the condition which applies the filter in the database
 */
class ScalarFilterEntry<T>(
    name: String,
    description: String,
    conditionGenerator: (property: Property, value: Expression) -> Condition
) : ScalarFilterEntryBase<T>(name, description, conditionGenerator) {
    override fun generateFilterEntry(scalarType: GraphQLInputType, neo4jName: String): FilterEntryDefinition {
        return SimpleFilterEntryDefinition<T>(name, description, scalarType, neo4jName, conditionGenerator)
    }
}
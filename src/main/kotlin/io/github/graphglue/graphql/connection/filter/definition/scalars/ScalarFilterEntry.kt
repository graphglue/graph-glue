package io.github.graphglue.graphql.connection.filter.definition.scalars

import graphql.schema.GraphQLInputType
import io.github.graphglue.graphql.connection.filter.definition.FilterEntryDefinition
import io.github.graphglue.graphql.connection.filter.definition.SimpleFilterEntryDefinition
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Expression
import org.neo4j.cypherdsl.core.Property

class ScalarFilterEntry<T>(
    name: String,
    description: String,
    conditionGenerator: (property: Property, value: Expression) -> Condition
) : ScalarFilterEntryBase<T>(name, description, conditionGenerator) {
    override fun generateFilterEntry(scalarType: GraphQLInputType, neo4jName: String): FilterEntryDefinition {
        return SimpleFilterEntryDefinition<T>(name, description, scalarType, neo4jName, conditionGenerator)
    }
}
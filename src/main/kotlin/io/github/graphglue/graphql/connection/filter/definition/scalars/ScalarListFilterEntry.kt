package io.github.graphglue.graphql.connection.filter.definition.scalars

import de.graphglue.graphql.connection.filter.definition.FilterEntryDefinition
import de.graphglue.graphql.connection.filter.definition.SimpleFilterEntryDefinition
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Expression
import org.neo4j.cypherdsl.core.Property

class ScalarListFilterEntry<T>(
    name: String,
    description: String,
    conditionGenerator: (property: Property, value: Expression) -> Condition
) : ScalarFilterEntryBase<T>(name, description, conditionGenerator) {
    override fun generateFilterEntry(scalarType: GraphQLInputType, neo4jName: String): FilterEntryDefinition {
        return SimpleFilterEntryDefinition<T>(
            name,
            description,
            GraphQLList(GraphQLNonNull(scalarType)),
            neo4jName,
            conditionGenerator
        )
    }
}
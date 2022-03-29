package io.github.graphglue.graphql.connection.filter.definition.scalars

import io.github.graphglue.graphql.connection.filter.definition.FilterEntryDefinition
import graphql.schema.GraphQLInputType
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Expression
import org.neo4j.cypherdsl.core.Property


abstract class ScalarFilterEntryBase<T>(
    val name: String,
    val description: String,
    val conditionGenerator: (property: Property, value: Expression) -> Condition
) {
    abstract fun generateFilterEntry(scalarType: GraphQLInputType, neo4jName: String): FilterEntryDefinition
}
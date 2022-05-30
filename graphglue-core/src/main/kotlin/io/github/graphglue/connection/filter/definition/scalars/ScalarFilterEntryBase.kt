package io.github.graphglue.connection.filter.definition.scalars

import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import io.github.graphglue.connection.filter.definition.FilterEntryDefinition
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Expression
import org.neo4j.cypherdsl.core.Parameter
import org.neo4j.cypherdsl.core.Property

/**
 * Base class for scalar filter entries
 * Can be used to define filters for a scalar property, e.g. an `in` or `eq` filter
 *
 * @param name the name of the field on the [GraphQLInputObjectType]
 * @param description the description of the field
 * @param conditionGenerator used to generate the condition which applies the filter in the database
 */
abstract class ScalarFilterEntryBase(
    val name: String,
    val description: String,
    val conditionGenerator: (property: Property, value: Parameter<*>) -> Condition
) {
    /**
     * Generates the [FilterEntryDefinition] used in the filter
     *
     * @param scalarType the type of the GraphQL filter field
     * @param neo4jName the name of the property of the node in the database (might be different from [name])
     * @return the generated definition of the filter entry
     */
    abstract fun generateFilterEntry(scalarType: GraphQLInputType, neo4jName: String): FilterEntryDefinition
}
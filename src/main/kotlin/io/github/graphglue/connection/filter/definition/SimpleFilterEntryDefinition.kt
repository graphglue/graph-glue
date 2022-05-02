package io.github.graphglue.connection.filter.definition

import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import io.github.graphglue.connection.filter.model.FilterEntry
import io.github.graphglue.connection.filter.model.SimpleFilterEntry
import io.github.graphglue.util.CacheMap
import org.neo4j.cypherdsl.core.*

/**
 * Definition for a single filter entry which filters by a Neo4j property using a [conditionGenerator]
 * to generate the condition.
 *
 * @param name the name of the field on the [GraphQLInputObjectType]
 * @param description the description of the field
 * @param type the field type in the GraphQL schema, should require no additional parsing (e.g. String, Int, ...)
 * @param neo4jName the name of the property on the node in the database (might be different from [name])
 * @param conditionGenerator used to generate the condition which is used in the database, takes the property
 *                           and the parameter as input
 */
class SimpleFilterEntryDefinition(
    name: String,
    description: String,
    private val type: GraphQLInputType,
    private val neo4jName: String,
    private val conditionGenerator: (property: Property, value: Parameter<*>) -> Condition
) : FilterEntryDefinition(name, description) {

    override fun parseEntry(value: Any?): FilterEntry {
        return SimpleFilterEntry(this, value!!)
    }

    override fun toGraphQLType(
        inputTypeCache: CacheMap<String, GraphQLInputType>,
    ) = type

    /**
     * Generates the condition based of the provided [Node]
     * Uses [conditionGenerator] to generate the condition
     *
     * @param node the CypherDSL [Node] on which the condition is based of
     * @param value the value which is wrapped in a CypherDSL property and then provided to the condition generator
     */
    fun generateCondition(node: Node, value: Any): Condition {
        return conditionGenerator(node.property(neo4jName), Cypher.anonParameter(value))
    }
}
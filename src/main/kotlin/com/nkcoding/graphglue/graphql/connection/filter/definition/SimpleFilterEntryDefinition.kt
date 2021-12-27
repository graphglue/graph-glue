package com.nkcoding.graphglue.graphql.connection.filter.definition

import graphql.schema.GraphQLInputType
import org.neo4j.cypherdsl.core.*

abstract class SimpleFilterEntryDefinition<T>(
    name: String,
    description: String,
    private val type: GraphQLInputType,
    private val neo4jName: String,
    private val conditionGenerator: (property: Property, value: Expression) -> Condition
) :
    FilterEntryDefinition(name, description) {
    override fun toGraphQLType(
        objectTypeCache: MutableMap<String, GraphQLInputType>,
    ) = type

    fun generateCondition(node: Node, value: T): Condition {
        return conditionGenerator(node.property(neo4jName), Cypher.anonParameter(value))
    }
}
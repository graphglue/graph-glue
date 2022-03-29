package io.github.graphglue.graphql.connection.filter.definition

import de.graphglue.graphql.connection.filter.model.FilterEntry
import de.graphglue.graphql.connection.filter.model.SimpleFilterEntry
import de.graphglue.util.CacheMap
import graphql.schema.GraphQLInputType
import org.neo4j.cypherdsl.core.*

class SimpleFilterEntryDefinition<T>(
    name: String,
    description: String,
    private val type: GraphQLInputType,
    private val neo4jName: String,
    private val conditionGenerator: (property: Property, value: Expression) -> Condition
) :
    FilterEntryDefinition(name, description) {

    @Suppress("UNCHECKED_CAST")
    override fun parseEntry(value: Any?): FilterEntry {
        return SimpleFilterEntry(this, value as T)
    }

    override fun toGraphQLType(
        inputTypeCache: CacheMap<String, GraphQLInputType>,
    ) = type

    fun generateCondition(node: Node, value: T): Condition {
        return conditionGenerator(node.property(neo4jName), Cypher.anonParameter(value))
    }
}
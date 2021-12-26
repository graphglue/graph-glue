package com.nkcoding.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.generator.scalars.ID
import org.springframework.data.neo4j.core.schema.GeneratedValue
import org.springframework.data.neo4j.core.schema.Id
import org.springframework.data.neo4j.core.support.UUIDStringGenerator

/**
 * Base class for all Nodes
 * This is always added to the schema
 * All domain entities which can be retrieved via the api
 * and should be persisted in the database should inherit from this class
 */
@Neo4jNode
@GraphQLDescription("Base class of all nodes")
abstract class Node(
    @GraphQLIgnore @Id @GeneratedValue(UUIDStringGenerator::class) val id: String
) {
    @GraphQLDescription("The unique id of this node")
    fun id(): ID {
        return ID(id)
    }
}
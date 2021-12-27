package com.nkcoding.graphglue.graphql.execution.definition

import com.nkcoding.graphglue.graphql.extensions.getPropertyName
import com.nkcoding.graphglue.model.Node
import com.nkcoding.graphglue.neo4j.execution.NodeQueryResult
import org.springframework.data.neo4j.core.schema.Relationship
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

abstract class RelationshipDefinition(
    val property: KProperty1<*, *>,
    val nodeKClass: KClass<out Node>,
    val type: String,
    val direction: Relationship.Direction,
    private val parentKClass: KClass<*>
) {
    val graphQLName get() = property.getPropertyName(parentKClass)

    fun generateRelationship(
        rootNode: org.neo4j.cypherdsl.core.Node,
        propertyNode: org.neo4j.cypherdsl.core.Node
    ): org.neo4j.cypherdsl.core.Relationship {
        return when (direction) {
            Relationship.Direction.OUTGOING -> rootNode.relationshipTo(propertyNode, type)
            Relationship.Direction.INCOMING -> rootNode.relationshipFrom(propertyNode, type)
        }
    }

    internal abstract fun <T : Node> registerQueryResult(node: Node, nodeQueryResult: NodeQueryResult<T>)
}
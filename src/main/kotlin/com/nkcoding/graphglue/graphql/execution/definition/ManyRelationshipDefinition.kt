package com.nkcoding.graphglue.graphql.execution.definition

import com.nkcoding.graphglue.model.Node
import com.nkcoding.graphglue.model.NodeSet
import com.nkcoding.graphglue.neo4j.execution.NodeQueryResult
import org.springframework.data.neo4j.core.schema.Relationship
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.jvmErasure

class ManyRelationshipDefinition(
    property: KProperty1<*, *>,
    type: String,
    direction: Relationship.Direction,
    parentKClass: KClass<*>
) : RelationshipDefinition(
    property,
    @Suppress("UNCHECKED_CAST") (property.returnType.arguments.first().type!!.jvmErasure as KClass<out Node>),
    type,
    direction,
    parentKClass
) {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Node> registerLocalQueryResult(node: Node, nodeQueryResult: NodeQueryResult<T>) {
        property as KProperty1<Node, NodeSet<T>>
        val nodeSet = property.get(node)
        nodeSet.registerQueryResult(nodeQueryResult)
    }
}
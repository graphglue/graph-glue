package de.graphglue.graphql.execution.definition

import de.graphglue.graphql.extensions.getDelegateAccessible
import de.graphglue.model.Node
import de.graphglue.model.NodeProperty
import de.graphglue.neo4j.execution.NodeQueryResult
import org.springframework.data.neo4j.core.schema.Relationship
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.jvmErasure

class OneRelationshipDefinition(
    property: KProperty1<*, *>, type: String, direction: Relationship.Direction, parentKClass: KClass<*>
) : RelationshipDefinition(
    property,
    @Suppress("UNCHECKED_CAST") (property.returnType.jvmErasure as KClass<out Node>),
    type,
    direction,
    parentKClass
) {
    override fun <T : Node> registerLocalQueryResult(node: Node, nodeQueryResult: NodeQueryResult<T>) {
        val nodeProperty = property.getDelegateAccessible<NodeProperty<T>>(node)
        nodeProperty.registerQueryResult(nodeQueryResult)
    }
}
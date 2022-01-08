package de.graphglue.neo4j.execution.definition

import de.graphglue.graphql.extensions.getDelegateAccessible
import de.graphglue.model.Direction
import de.graphglue.model.Node
import de.graphglue.model.NodeProperty
import de.graphglue.neo4j.execution.NodeQueryResult
import de.graphglue.neo4j.repositories.RelationshipDiff
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.jvmErasure

class OneRelationshipDefinition(
    property: KProperty1<*, *>, type: String, direction: Direction, parentKClass: KClass<*>
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

    override fun getRelationshipDiff(node: Node, nodeIdLookup: Map<Node, String>): RelationshipDiff {
        val nodeProperty = property.getDelegateAccessible<NodeProperty<*>>(node)
        return nodeProperty.getRelationshipDiff(nodeIdLookup)
    }

    override fun getRelatedNodesToSave(node: Node): Collection<Node> {
        val nodeProperty = property.getDelegateAccessible<NodeProperty<*>>(node)
        nodeProperty.ensureValidSaveState()
        return nodeProperty.getRelatedNodesToSave()
    }
}
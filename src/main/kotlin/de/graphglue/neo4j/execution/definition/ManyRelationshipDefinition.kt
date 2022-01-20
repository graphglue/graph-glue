package de.graphglue.neo4j.execution.definition

import de.graphglue.model.Direction
import de.graphglue.model.Node
import de.graphglue.model.NodeSet
import de.graphglue.neo4j.execution.NodeQueryResult
import de.graphglue.neo4j.repositories.RelationshipDiff
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.jvmErasure

/**
 * Defines the many side of a many-to-many or many-to-one relationship between two [Node]s
 *
 * @param property the property on the class which defines the relationship
 * @param type the type of the relation (label associated with Neo4j relationship)
 * @param direction direction of the relation (direction associated with Neo4j relationship)
 * @param parentKClass the class associated with the [NodeDefinition] this is used as part of,
 *                        must be a subclass of the property defining class
 */
class ManyRelationshipDefinition(
    property: KProperty1<*, *>,
    type: String,
    direction: Direction,
    parentKClass: KClass<out Node>
) : RelationshipDefinition(
    property,
    @Suppress("UNCHECKED_CAST") (property.returnType.arguments.first().type!!.jvmErasure as KClass<out Node>),
    type,
    direction,
    parentKClass
) {

    override fun <T : Node> registerLocalQueryResult(node: Node, nodeQueryResult: NodeQueryResult<T>) {
        getNodeSet<T>(node).registerQueryResult(nodeQueryResult)
    }

    override fun getRelationshipDiff(node: Node, nodeIdLookup: Map<Node, String>): RelationshipDiff {
        return getNodeSet<Node>(node).getRelationshipDiff(nodeIdLookup)
    }

    override fun getRelatedNodesToSave(node: Node): Collection<Node> {
        return getNodeSet<Node>(node).getRelatedNodesToSave()
    }

    /**
     * Gets the value of the property
     *
     * @param node the instance with the property
     * @return the value of the property
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Node> getNodeSet(node: Node): NodeSet<T> {
        property as KProperty1<Node, NodeSet<T>>
        return property.get(node)
    }
}
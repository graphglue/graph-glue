package io.github.graphglue.definition

import io.github.graphglue.data.repositories.RelationshipDiff
import io.github.graphglue.model.Direction
import io.github.graphglue.model.Node
import io.github.graphglue.model.NodeRelationship
import io.github.graphglue.model.property.BaseNodePropertyDelegate
import io.github.graphglue.model.property.NodePropertyDelegate
import org.neo4j.cypherdsl.core.ExposesPatternLengthAccessors
import org.neo4j.cypherdsl.core.ExposesRelationships
import org.neo4j.cypherdsl.core.RelationshipPattern
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties


/**
 * Defines a relationship between two [Node]s
 * There may or may not be an inverse relation on the foreign node
 *
 * @param property the property on the class which defines the relationship
 * @param nodeKClass the class associated with the item nodes
 * @param type the type of the relation (label associated with Neo4j relationship)
 * @param direction direction of the relation (direction associated with Neo4j relationship)
 * @param parentKClass the class associated with the [NodeDefinition] this is used as part of,
 *                     must be a subclass of the property defining class
 * @param allowedAuthorizations the names of authorizations which allow via this relation.
 *                              These names result in properties with value `true` on the relation
 */
abstract class RelationshipDefinition(
    val property: KProperty1<*, *>,
    val nodeKClass: KClass<out Node>,
    val type: String,
    val direction: Direction,
    val parentKClass: KClass<out Node>,
    val allowedAuthorizations: Set<String>
) {

    /**
     * optional setter which is used to initialize the opposite property
     * may only be present if the opposite side is a one side
     */
    internal val remotePropertySetter: RemotePropertySetter? = generateRemotePropertySetter()

    /**
     * Creates the remote property setter.
     * Checks all properties on the remote node, and returns the first where
     * - the type matches
     * - the direction is opposite
     * - the property is a one property
     *
     * @return the setter if possible, otherwise null
     */
    private fun generateRemotePropertySetter(): RemotePropertySetter? {
        for (remoteProperty in nodeKClass.memberProperties) {
            val annotation = remoteProperty.findAnnotation<NodeRelationship>()
            if (annotation?.type == type && annotation.direction != direction) {
                if (remoteProperty.returnType.isSubtypeOf(Node::class.createType())) {
                    return { remoteNode, value ->
                        val nodeProperty = remoteNode.getProperty<NodePropertyDelegate<Node?>>(remoteProperty)
                        nodeProperty.setFromRemote(value)
                    }
                }
            }
        }
        return null
    }

    /**
     * Generates a Cypher-DSL RelationshipPattern
     *
     * @param rootNode the start node of the relationship
     * @param propertyNode the related node
     * @param T the type of the generated relationship
     * @return the generated relationship pattern
     */
    fun <T> generateRelationship(
        rootNode: ExposesRelationships<T>, propertyNode: org.neo4j.cypherdsl.core.Node
    ): T where T : RelationshipPattern, T : ExposesPatternLengthAccessors<*> {
        return when (direction) {
            Direction.OUTGOING -> rootNode.relationshipTo(propertyNode, type)
            Direction.INCOMING -> rootNode.relationshipFrom(propertyNode, type)
        }
    }

    /**
     * Gets the diff describing updates of the property
     *
     * @param node the node which contains the property to get the diff from
     * @return the diff describing added and removed nodes
     */
    internal fun getRelationshipDiff(node: Node): RelationshipDiff {
        return node.getProperty<BaseNodePropertyDelegate<Node, *>>(property).getRelationshipDiff()
    }

    /**
     * Validates the relationship for [Node] by calling [Node]
     */
    internal fun validate(
        node: Node,
        savingNodes: Set<Node>,
        nodeDefinitionCollection: NodeDefinitionCollection
    ) {
        node.getProperty<BaseNodePropertyDelegate<Node, *>>(property)
            .validate(savingNodes, this, nodeDefinitionCollection)
    }

    /**
     * Gets related nodes to save
     *
     * @param node the node which contains the property to get the related nodes to save
     * @return a list of nodes to save
     */
    internal fun getRelatedNodesToSave(node: Node): Collection<Node> {
        return node.getProperty<BaseNodePropertyDelegate<Node, *>>(property).getRelatedNodesToSave()
    }

    /**
     * Gets related nodes defined by this property, but only those already loaded (therefore no lazy loading)
     * The relationships do not have to be persisted yet
     *
     * @param node the node which contains the property to get the loaded related nodes
     * @return the already loaded related nodes
     */
    internal fun getLoadedRelatedNodes(node: Node): Collection<Node> {
        return node.getProperty<BaseNodePropertyDelegate<Node, *>>(property).getLoadedRelatedNodes()
    }
}

/**
 * Alias for the setter function for remote properties
 */
private typealias RemotePropertySetter = (remoteNode: Node, value: Node) -> Unit
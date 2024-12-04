package io.github.graphglue.model.property

import io.github.graphglue.data.execution.NodeQueryResult
import io.github.graphglue.data.repositories.RelationshipDiff
import io.github.graphglue.definition.FieldDefinition
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.definition.RelationshipDefinition
import io.github.graphglue.definition.extensions.firstTypeArgument
import io.github.graphglue.model.Node
import io.github.graphglue.model.property.NodeSetPropertyDelegate.NodeSetProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType

/**
 * Property for the one side of a relation
 * Depending on the type of `property` may be an optional property
 *
 * @param parent see [BaseNodePropertyDelegate.parent]
 * @param property see [BaseNodePropertyDelegate.property]
 */
class NodePropertyDelegate<T : Node?>(
    parent: Node, property: KProperty1<*, *>
) : BaseNodePropertyDelegate<T, NodePropertyDelegate<T>.NodeProperty>(parent, property) {

    /**
     * The [NodeSetProperty] returned to the user
     */
    private val nodeProperty = NodeProperty()

    /**
     * If `true`, the value of this property is already loaded (either from the database or from another value)
     */
    private var isLoaded = false

    /**
     * The current value of this property, may or may not be persisted to the database
     */
    private var currentNode: T? = null

    /**
     * The related node as in the database
     * `null` if not loaded or no relation present in the database
     */
    private var persistedNode: T? = null

    /**
     * True if the [T] is marked nullable
     */
    private val supportsNull: Boolean
        get() {
            val type = property.returnType.firstTypeArgument
            return type.isMarkedNullable || type.classifier is KTypeParameter
        }

    override fun registerQueryResult(
        fieldDefinition: FieldDefinition,
        queryResult: NodeQueryResult<T & Any>
    ) {
        if (!isLoaded && queryResult.options.isAllQuery) {
            super.registerQueryResult(fieldDefinition, queryResult)
            if (queryResult.nodes.size > 1) {
                throw IllegalArgumentException("Too many nodes for one side of relation $propertyName")
            }
            currentNode = queryResult.nodes.firstOrNull()
            persistedNode = currentNode
            isLoaded = true
        }
    }

    override fun getRelationshipDiff(): RelationshipDiff {
        val current = currentNode
        val nodesToRemove = if (current != persistedNode && persistedNode != null) {
            listOf(persistedNode!!)
        } else {
            emptyList()
        }
        val nodesToAdd = if (current != persistedNode && current != null) {
            listOf(current)
        } else {
            emptyList()
        }
        return RelationshipDiff(nodesToAdd, nodesToRemove)
    }

    override fun getRelatedNodesToSave(): Collection<Node> {
        val current = currentNode
        return if (current != persistedNode && current != null) {
            listOf(current)
        } else {
            emptyList()
        }
    }

    override fun getLoadedRelatedNodes(): Collection<Node> {
        return listOfNotNull(currentNode)
    }

    /**
     * Ensures that this property is loaded
     *
     * @param cache used to load nodes from, if provided, not loading deleted nodes
     * @param loader if provided used to define nested nodes to load
     */
    private suspend fun ensureLoaded(cache: NodeCache?, loader: (LazyLoadingSubqueryGenerator<T>.() -> Unit)?) {
        if (!isLoaded) {
            if (parent.isPersisted) {
                parent.loadNodesOfRelationship(property, loader)
            }
            persistedNode = currentNode
            isLoaded = true
        }
        if (cache != null && nodeCache == null) {
            nodeCache = cache
            val newNode = cache.getOrAdd(currentNode)
            if (currentNode == persistedNode) {
                persistedNode = newNode
            }
            currentNode = newNode
        }
    }

    /**
     * Sets the node from the remote side
     * Used to prevent unnecessary lazy loaded queries
     *
     * @param value the value loaded from the database
     */
    internal fun setFromRemote(value: T) {
        if (!isLoaded) {
            currentNode = value
            persistedNode = value
            isLoaded = true
        }
    }

    override suspend fun getLoadedProperty(
        cache: NodeCache?, loader: (LazyLoadingSubqueryGenerator<T>.() -> Unit)?
    ): NodeProperty {
        ensureLoaded(cache, loader)
        return nodeProperty
    }

    override fun validate(
        savingNodes: Set<Node>,
        relationshipDefinition: RelationshipDefinition,
        nodeDefinitionCollection: NodeDefinitionCollection
    ) {
        if (!supportsNull) {
            val neverSetInitialRelationship = currentNode == null && !parent.isPersisted
            val removedRequiredRelationship = currentNode == null && persistedNode != null
            if (neverSetInitialRelationship || removedRequiredRelationship) {
                val setByOtherSide = savingNodes.filter { relationshipDefinition.nodeKClass.isInstance(it) }.any {
                    val relatedNodeDefinition = nodeDefinitionCollection.getNodeDefinition(it::class)
                    val inverseRelationshipDefinition =
                        relatedNodeDefinition.getRelationshipDefinitionByInverse(relationshipDefinition)
                    inverseRelationshipDefinition?.getLoadedRelatedNodes(it)?.contains(parent) ?: false
                }
                if (!setByOtherSide) {
                    throw IllegalStateException(
                        "Non-nullable property $propertyName cannot be saved, as it has value null and is not set by other side."
                    )
                }
            }
        }
    }

    /**
     * Node property representing the one side of a [Node] relation
     * [value] can be used to get/set the node
     */
    inner class NodeProperty {

        /**
         * The current value of the property
         */
        @Suppress("UNCHECKED_CAST")
        var value: T
            get() {
                assert(isLoaded)
                if (!supportsNull && currentNode == null) {
                    throw IllegalStateException("The non-nullable property $propertyName has a null value")
                }
                return currentNode as T
            }
            set(value) {
                assert(isLoaded)
                if (value != currentNode) {
                    currentNode = value
                }
            }
    }
}

/**
 * Type which can be used to check the return type of node properties
 */
val NODE_PROPERTY_TYPE = LazyLoadingDelegate::class.createType(
    listOf(
        KTypeProjection.covariant(Node::class.createType(nullable = true)), KTypeProjection.covariant(
            NodePropertyDelegate.NodeProperty::class.createType(
                listOf(
                    KTypeProjection.covariant(
                        Node::class.createType(nullable = true)
                    )
                )
            )
        )
    )
)
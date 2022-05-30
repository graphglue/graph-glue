package io.github.graphglue.model.property

import graphql.execution.DataFetcherResult
import io.github.graphglue.data.execution.DEFAULT_PART_ID
import io.github.graphglue.data.execution.NodeQuery
import io.github.graphglue.data.execution.NodeQueryParser
import io.github.graphglue.data.execution.NodeQueryResult
import io.github.graphglue.data.repositories.RelationshipDiff
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.definition.extensions.firstTypeArgument
import io.github.graphglue.model.Node
import io.github.graphglue.model.property.NodeSetPropertyDelegate.NodeSetProperty
import org.neo4j.cypherdsl.core.Cypher
import kotlin.reflect.KProperty1
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType

/**
 * Property for the one side of a relation
 * Depending on the type of `property` may be an optional property
 *
 * @param parent see [BasePropertyDelegate.parent]
 * @param property see [BasePropertyDelegate.property]
 */
class NodePropertyDelegate<T : Node?>(
    parent: Node, property: KProperty1<*, *>
) : BasePropertyDelegate<T, NodePropertyDelegate<T>.NodeProperty>(parent, property) {

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
    private val supportsNull get() = property.returnType.firstTypeArgument.isMarkedNullable

    override fun registerQueryResult(nodeQueryResult: NodeQueryResult<T>) {
        super.registerQueryResult(nodeQueryResult)
        if (!isLoaded && nodeQueryResult.options.isAllQuery) {
            if (nodeQueryResult.nodes.size > 1) {
                throw IllegalArgumentException("Too many  nodes for one side of relation")
            }
            currentNode = nodeQueryResult.nodes.firstOrNull()
            persistedNode = currentNode
            isLoaded = true
        }
    }

    override fun getRelationshipDiff(
        nodeDefinition: NodeDefinition
    ): RelationshipDiff {
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
        ensureValidSaveState()
        val current = currentNode
        return if (current != persistedNode && current != null) {
            listOf(current)
        } else {
            emptyList()
        }
    }

    override fun constructGraphQLResult(
        result: NodeQueryResult<T>, localContext: NodeQuery?, nodeQueryParser: NodeQueryParser
    ): DataFetcherResult<*> {
        return DataFetcherResult.newResult<T>().data(result.nodes.firstOrNull())
            .localContext(localContext?.parts?.get(DEFAULT_PART_ID)).build()
    }

    /**
     * Ensures that this property is loaded
     */
    private suspend fun ensureLoaded() {
        if (!isLoaded) {
            val (result, _) = parent.loadNodesOfRelationship<T>(property)
            currentNode = result.nodes.firstOrNull()
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

    /**
     * Ensures that this property is in a valid state
     *
     * @throws IllegalStateException if in an invalid state
     */
    private fun ensureValidSaveState() {
        if (!supportsNull) {
            val neverSetInitialRelationship = currentNode == null && parent.rawId == null
            val removedRequiredRelationship = currentNode == null && persistedNode != null
            if (neverSetInitialRelationship || removedRequiredRelationship) {
                throw IllegalStateException("Non-nullable property $property cannot be saved, as it has value null")
            }
        }
    }

    override suspend fun getLoadedProperty(): NodeProperty {
        ensureLoaded()
        return nodeProperty
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
                    throw IllegalStateException("The non-nullable property $property has a null value")
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
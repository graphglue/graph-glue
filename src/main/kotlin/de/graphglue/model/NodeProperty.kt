package de.graphglue.model

import de.graphglue.neo4j.execution.*
import de.graphglue.neo4j.repositories.RelationshipDiff
import graphql.execution.DataFetcherResult
import kotlinx.coroutines.runBlocking
import org.neo4j.cypherdsl.core.Cypher
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

/**
 * Property for the one side of a relation
 * Depending on the type of `property` may be an optional property
 *
 * @param value the current value of the property, `null` meaning not loaded
 * @param parent see [BaseProperty.parent]
 * @param property see [BaseProperty.property]
 */
class NodeProperty<T : Node?>(
    value: T? = null,
    parent: Node,
    property: KProperty1<*, *>
) : BaseProperty<T>(parent, property) {

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
    private val supportsNull get() = property.returnType.isMarkedNullable

    init {
        if (value != null) {
            isLoaded = true
            currentNode = value
        }
    }

    /**
     * Gets the value of the property
     * loads if from the database if necessary
     *
     * @param thisRef the node which has this property
     * @param property the represented property
     * @return the current value
     */
    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Node, property: KProperty<*>): T {
        return runBlocking {
            val value = getCurrentNode()
            if (supportsNull) {
                value as T
            } else {
                if (value == null) {
                    throw IllegalStateException("The non-nullable property $property has a null value")
                }
                value
            }
        }
    }

    /**
     * Sets the current value
     * Loads the one from the database first
     *
     * @param thisRef the node which has this property
     * @param property the represented property
     */
    operator fun setValue(thisRef: Node, property: KProperty<*>, value: T) {
        runBlocking {
            val current = getCurrentNode()
            if (value != current) {
                currentNode = value
            }
        }
    }

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

    override fun getRelationshipDiff(nodeIdLookup: Map<Node, String>): RelationshipDiff {
        val current = currentNode
        val nodesToRemove = if (current != persistedNode) {
            listOf(Cypher.anyNode())
        } else {
            emptyList()
        }
        val nodesToAdd = if (current != persistedNode && current != null) {
            val idParameter = Cypher.anonParameter(nodeIdLookup[current])
            listOf(Cypher.anyNode().withProperties(mapOf("id" to idParameter)))
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
        result: NodeQueryResult<T>,
        localContext: NodeQuery?,
        nodeQueryParser: NodeQueryParser
    ): DataFetcherResult<*> {
        return DataFetcherResult.newResult<T>()
            .data(result.nodes.firstOrNull())
            .localContext(localContext?.parts?.get(DEFAULT_PART_ID))
            .build()
    }

    /**
     * Gets the current node
     * If not loaded, loads it from the database
     *
     * @return the current node
     */
    private suspend fun getCurrentNode(): T? {
        return if (!isLoaded) {
            val (result, _) = parent.loadNodesOfRelationship<T>(property)
            currentNode = result.nodes.firstOrNull()
            currentNode
        } else {
            currentNode
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
}
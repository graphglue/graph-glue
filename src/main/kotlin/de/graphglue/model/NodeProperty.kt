package de.graphglue.model

import de.graphglue.neo4j.execution.*
import de.graphglue.neo4j.repositories.RelationshipDiff
import graphql.execution.DataFetcherResult
import kotlinx.coroutines.runBlocking
import org.neo4j.cypherdsl.core.Cypher
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

class NodeProperty<T : Node?>(
    value: T? = null,
    parent: Node,
    property: KProperty1<*, *>
) : BaseProperty<T>(parent, property) {

    private var isLoaded = false
    private var currentNode: T? = null
    private var persistedNode: T? = null

    private val supportsNull get() = property.returnType.isMarkedNullable

    init {
        if (value != null) {
            isLoaded = true
            currentNode = value
        }
    }

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

    private suspend fun getCurrentNode(): T? {
        return if (!isLoaded) {
            val (result, _) = parent.loadNodesOfRelationship<T>(property)
            currentNode = result.nodes.firstOrNull()
            currentNode
        } else {
            currentNode
        }
    }

    internal fun setFromRemote(value: T) {
        if (!isLoaded) {
            currentNode = value
            persistedNode = value
            isLoaded = true
        }
    }

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
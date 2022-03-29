package io.github.graphglue.model

import io.github.graphglue.neo4j.execution.NodeQuery
import io.github.graphglue.neo4j.execution.NodeQueryParser
import io.github.graphglue.neo4j.execution.NodeQueryResult
import io.github.graphglue.neo4j.repositories.RelationshipDiff
import graphql.execution.DataFetcherResult
import kotlinx.coroutines.runBlocking
import org.neo4j.cypherdsl.core.Cypher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import java.util.*
import kotlin.collections.HashSet
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

/**
 * Property for the many side of a relation
 * Is mapped to a [Connection] in GraphQL
 *
 * @param value the current value of the property, `null` meaning not loaded
 * @param parent see [BaseProperty.parent]
 * @param property see [BaseProperty.property]
 */
class NodeSetProperty<T : Node>(
    value: Collection<T>? = null,
    parent: Node,
    property: KProperty1<*, *>
) : BaseProperty<T>(parent, property) {
    /**
     * The [Set] stub returned to the user
     */
    private var nodeSet: NodeSet = NodeSet()

    /**
     * Newly added nodes, relations must be added to the database
     */
    private val addedNodes = HashSet<T>()

    /**
     * Newly removed nodes, relations must be removed from the database
     */
    private val removedNodes = HashSet<T>()

    /**
     * Current set of nodes
     */
    private var currentNodes: MutableSet<T>? = null

    /**
     * `true` iff the current values are loaded from the database or set via constructor
     */
    private val isLoaded get() = currentNodes != null

    init {
        if (value != null) {
            val current = HashSet(value)
            currentNodes = current
            addedNodes.addAll(current)
        }
    }

    /**
     * Gets the value of the property
     *
     * @param thisRef the node which has this property
     * @param property the represented property
     * @return the stub which handles set functionality
     */
    operator fun getValue(thisRef: Node, property: KProperty<*>): NodeSet = nodeSet

    override fun registerQueryResult(nodeQueryResult: NodeQueryResult<T>) {
        super.registerQueryResult(nodeQueryResult)
        if (!isLoaded && nodeQueryResult.options.isAllQuery) {
            currentNodes = HashSet(nodeQueryResult.nodes)
        }
    }

    override fun constructGraphQLResult(
        result: NodeQueryResult<T>,
        localContext: NodeQuery?,
        nodeQueryParser: NodeQueryParser
    ): DataFetcherResult<*> {
        val connection = Connection.fromQueryResult(result, nodeQueryParser.objectMapper)
        return DataFetcherResult.newResult<Connection<T>>()
            .data(connection)
            .localContext(localContext)
            .build()
    }

    override fun getRelationshipDiff(nodeIdLookup: Map<Node, String>): RelationshipDiff {
        return RelationshipDiff(
            addedNodes.map {
                val idParameter = Cypher.anonParameter(nodeIdLookup[it])
                Cypher.anyNode().withProperties(mapOf("id" to idParameter))
            },
            removedNodes.filter { it.rawId != null }.map {
                val idParameter = Cypher.anonParameter(it.rawId!!)
                Cypher.anyNode().withProperties(mapOf("id" to idParameter))
            }
        )
    }

    override fun getRelatedNodesToSave(): Collection<Node> {
        return addedNodes
    }

    /**
     * Gets the current set of nodes
     * Lazy loads if from the database if necessary
     */
    private suspend fun getCurrentNodes(): MutableSet<T> {
        if (!isLoaded) {
            val (result, _) = parent.loadNodesOfRelationship<T>(property)
            currentNodes = result.nodes.toMutableSet()
        }
        return currentNodes!!
    }

    /**
     * Stub which handles set functionality
     * Lazy loads both on read, but also on any write (add, remove)
     * The iterator supports remove
     */
    inner class NodeSet : AbstractSet<T>(), MutableSet<T> {

        override val size get() = runBlocking { getCurrentNodes().size }

        override fun contains(element: T) = runBlocking { getCurrentNodes().contains(element) }

        override fun add(element: T): Boolean {
            return runBlocking {
                val currentNodes = getCurrentNodes()
                val result = currentNodes.add(element)
                if (result) {
                    if (!removedNodes.remove(element)) {
                        addedNodes.add(element)
                    }
                }
                result
            }
        }

        override fun remove(element: T): Boolean {
            return runBlocking {
                val currentNodes = getCurrentNodes()
                val result = currentNodes.remove(element)
                if (result) {
                    if (!addedNodes.remove(element)) {
                        removedNodes.add(element)
                    }
                }
                result
            }
        }

        override fun iterator(): MutableIterator<T> {
            return runBlocking { NodeSetIterator(getCurrentNodes().iterator()) }
        }

        /**
         * Iterator which supports remove and delegates all other functionality to parent iterator
         */
        inner class NodeSetIterator(private val parentIterator: MutableIterator<T>) : MutableIterator<T> {
            /**
             * The current value, which should be removed on [remove]
             */
            private var current: T? = null

            override fun hasNext() = parentIterator.hasNext()

            override fun next(): T {
                val next = parentIterator.next()
                current = next
                return next
            }

            override fun remove() {
                parentIterator.remove()
                val current = current!!
                if (!addedNodes.remove(current)) {
                    removedNodes.add(current)
                }
            }

        }

    }
}
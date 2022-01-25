package de.graphglue.model

import de.graphglue.neo4j.execution.NodeQuery
import de.graphglue.neo4j.execution.NodeQueryParser
import de.graphglue.neo4j.execution.NodeQueryResult
import de.graphglue.neo4j.repositories.RelationshipDiff
import graphql.execution.DataFetcherResult
import kotlinx.coroutines.runBlocking
import org.neo4j.cypherdsl.core.Cypher
import java.util.*
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

class NodeSetProperty<T : Node>(
    value: Collection<T>? = null,
    parent: Node,
    property: KProperty1<*, *>
) : BaseProperty<T>(parent, property) {
    private var nodeSet: NodeSet = NodeSet()
    private val addedNodes = HashSet<T>()
    private val removedNodes = HashSet<T>()
    private var currentNodes: MutableSet<T>? = null

    private val isLoaded get() = currentNodes != null

    init {
        if (value != null) {
            val current = HashSet(value)
            currentNodes = current
            addedNodes.addAll(current)
        }
    }

    operator fun getValue(thisRef: Node, property: KProperty<*>): NodeSet = nodeSet

    override fun registerQueryResult(nodeQueryResult: NodeQueryResult<T>) {
        super.registerQueryResult(nodeQueryResult)
        if (!isLoaded && nodeQueryResult.options.isAllQuery) {
            setCurrentNodes(nodeQueryResult.nodes)
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

    private fun setCurrentNodes(nodes: Collection<T>): MutableSet<T> {
        assert(!isLoaded) { "cannot set current nodes as these are already loaded" }
        val copy = HashSet(nodes)
        copy.removeAll(removedNodes.toSet())
        for (node in addedNodes) {
            if (node !in copy) {
                copy.add(node)
            }
        }
        currentNodes = copy
        return copy
    }

    private suspend fun getCurrentNodes(): MutableSet<T> {
        if (!isLoaded) {
            val (result, _) = parent.loadNodesOfRelationship<T>(property)
            currentNodes = result.nodes.toMutableSet()
        }
        return currentNodes!!
    }

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

        inner class NodeSetIterator(private val parentIterator: MutableIterator<T>) : MutableIterator<T> {
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
package io.github.graphglue.model

import graphql.execution.DataFetcherResult
import io.github.graphglue.data.execution.NodeQuery
import io.github.graphglue.data.execution.NodeQueryParser
import io.github.graphglue.data.execution.NodeQueryResult
import io.github.graphglue.data.repositories.RelationshipDiff
import io.github.graphglue.definition.NodeDefinition
import org.neo4j.cypherdsl.core.Cypher
import java.util.*
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType

/**
 * Property for the many side of a relation
 * Is mapped to a [Connection] in GraphQL
 *
 * @param parent see [BaseProperty.parent]
 * @param property see [BaseProperty.property]
 */
class NodeSetProperty<T : Node>(
    parent: Node, property: KProperty1<*, *>
) : BaseProperty<T>(parent, property) {
    /**
     * The [Set] stub returned to the user
     */
    private val nodeSet = NodeSet()

    /**
     * Can be used to get the loaded [nodeSet], returned in getter
     */
    private val lazyLoadingDelegate = LazyLoadingDelegate<MutableSet<T>>()

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

    /**
     * Gets the property itself
     *
     * @param thisRef the node which has this property
     * @param property the represented property
     * @return a delegate which can be used to get the value of the property
     */
    operator fun getValue(thisRef: Node, property: KProperty<*>) = lazyLoadingDelegate

    override fun registerQueryResult(nodeQueryResult: NodeQueryResult<T>) {
        super.registerQueryResult(nodeQueryResult)
        if (!isLoaded && nodeQueryResult.options.isAllQuery) {
            currentNodes = HashSet(nodeQueryResult.nodes)
        }
    }

    override fun constructGraphQLResult(
        result: NodeQueryResult<T>, localContext: NodeQuery?, nodeQueryParser: NodeQueryParser
    ): DataFetcherResult<*> {
        val connection = Connection.fromQueryResult(result, nodeQueryParser.objectMapper)
        return DataFetcherResult.newResult<Connection<T>>().data(connection).localContext(localContext).build()
    }

    override fun getRelationshipDiff(
        nodeIdLookup: Map<Node, String>, nodeDefinition: NodeDefinition
    ): RelationshipDiff {
        return RelationshipDiff(addedNodes.map {
            val idParameter = Cypher.anonParameter(nodeIdLookup[it])
            nodeDefinition.node().withProperties(mapOf("id" to idParameter))
        }, removedNodes.filter { it.rawId != null }.map {
            val idParameter = Cypher.anonParameter(it.rawId!!)
            nodeDefinition.node().withProperties(mapOf("id" to idParameter))
        })
    }

    override fun getRelatedNodesToSave(): Collection<Node> {
        return addedNodes
    }

    /**
     * Ensures that this [NodeSet] is loaded
     */
    private suspend fun ensureLoaded() {
        if (!isLoaded) {
            val (result, _) = parent.loadNodesOfRelationship<T>(property)
            currentNodes = result.nodes.toMutableSet()
        }
    }

    /**
     * Delegates which ensures that the [nodeSet] is loaded
     *
     * @param R explicit type necessary for reflection
     */
    inner class LazyLoadingDelegate<R : MutableSet<T>> : BaseProperty.LazyLoadingDelegate<R> {

        /**
         * Gets the loaded value of this property
         *
         * @return the loaded [NodeSet]
         */
        suspend fun get(): MutableSet<T> {
            ensureLoaded()
            return nodeSet
        }

    }

    /**
     * Stub which handles set functionality
     * Lazy loads both on read, but also on any write (add, remove)
     * The iterator supports remove
     */
    inner class NodeSet : AbstractSet<T>(), MutableSet<T> {

        override val size: Int
            get() {
                assert(isLoaded)
                return currentNodes!!.size
            }

        override fun contains(element: T): Boolean {
            assert(isLoaded)
            return currentNodes!!.contains(element)
        }

        override fun add(element: T): Boolean {
            assert(isLoaded)
            val result = currentNodes!!.add(element)
            if (result) {
                if (!removedNodes.remove(element)) {
                    addedNodes.add(element)
                }
            }
            return result
        }

        override fun remove(element: T): Boolean {
            assert(isLoaded)
            val result = currentNodes!!.remove(element)
            if (result) {
                if (!addedNodes.remove(element)) {
                    removedNodes.add(element)
                }
            }
            return result
        }

        override fun iterator(): MutableIterator<T> {
            assert(isLoaded)
            return NodeSetIterator(currentNodes!!.iterator())
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

/**
 * Type which can be used to check the return type of node set properties
 */
val NODE_SET_PROPERTY_TYPE = BaseProperty.LazyLoadingDelegate::class.createType(
    listOf(
        KTypeProjection.covariant(Set::class.createType(listOf(KTypeProjection.covariant(Node::class.createType()))))
    )
)
package io.github.graphglue.model.property

import io.github.graphglue.connection.model.Connection
import io.github.graphglue.data.execution.NodeQueryResult
import io.github.graphglue.data.repositories.RelationshipDiff
import io.github.graphglue.definition.FieldDefinition
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.definition.RelationshipDefinition
import io.github.graphglue.model.Node
import java.util.*
import kotlin.reflect.KProperty1
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType

/**
 * Property for the many side of a relation
 * Is mapped to a [Connection] in GraphQL
 *
 * @param parent see [BaseNodePropertyDelegate.parent]
 * @param property see [BaseNodePropertyDelegate.property]
 */
class NodeSetPropertyDelegate<T : Node>(
    parent: Node, property: KProperty1<*, *>
) : BaseNodePropertyDelegate<T, NodeSetPropertyDelegate<T>.NodeSetProperty>(parent, property) {

    /**
     * The [NodeSetProperty] returned to the user
     */
    private val nodeSetProperty = NodeSetProperty()

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

    override fun registerQueryResult(
        fieldDefinition: FieldDefinition,
        queryResult: NodeQueryResult<T>
    ) {
        if (!isLoaded && queryResult.options.isAllQuery) {
            super.registerQueryResult(fieldDefinition, queryResult)
            currentNodes = HashSet(queryResult.nodes)
        }
    }

    override fun getRelationshipDiff(): RelationshipDiff {
        return RelationshipDiff(addedNodes, removedNodes.filter { it.id != null })
    }

    override fun getRelatedNodesToSave(): Collection<Node> {
        return addedNodes
    }

    override fun getLoadedRelatedNodes(): Collection<Node> {
        return currentNodes ?: emptyList()
    }

    /**
     * Ensures that this [NodeSetProperty] is loaded
     *
     * @param cache used to load nodes from, if provided, not loading deleted nodes
     * @param loader if provided used to define nested nodes to load
     */
    private suspend fun ensureLoaded(cache: NodeCache?, loader: (LazyLoadingSubqueryGenerator<T>.() -> Unit)?) {
        if (!isLoaded) {
            if (parent.isPersisted) {
                parent.loadNodesOfRelationship(property, loader)
            } else {
                currentNodes = mutableSetOf()
            }
        }
        assert(isLoaded) { "Failed to load $property of $parent" }
        if (cache != null && nodeCache != cache) {
            nodeCache = cache
            currentNodes = currentNodes!!.mapNotNull(cache::getOrAdd).toMutableSet()
        }
    }

    override suspend fun getLoadedProperty(cache: NodeCache?, loader: (LazyLoadingSubqueryGenerator<T>.() -> Unit)?): NodeSetProperty {
        ensureLoaded(cache, loader)
        return nodeSetProperty
    }

    override fun validate(
        savingNodes: Set<Node>,
        relationshipDefinition: RelationshipDefinition,
        nodeDefinitionCollection: NodeDefinitionCollection
    ) {}

    /**
     * Node property representing the many side of a node relation.
     * Provides set functionality.
     * The iterator supports remove.
     */
    inner class NodeSetProperty : AbstractSet<T>(), MutableSet<T> {

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
val NODE_SET_PROPERTY_TYPE = LazyLoadingDelegate::class.createType(
    listOf(
        KTypeProjection.covariant(Node::class.createType(nullable = true)), KTypeProjection.covariant(
            NodeSetPropertyDelegate.NodeSetProperty::class.createType(
                listOf(
                    KTypeProjection.covariant(
                        Node::class.createType(nullable = true)
                    )
                )
            )
        )
    )
)
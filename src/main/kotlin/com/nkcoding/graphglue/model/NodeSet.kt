package com.nkcoding.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.nkcoding.graphglue.graphql.execution.QueryOptions
import com.nkcoding.graphglue.graphql.execution.QueryParser
import com.nkcoding.graphglue.graphql.redirect.RedirectPropertyClass
import com.nkcoding.graphglue.neo4j.execution.NodeQueryResult
import graphql.schema.DataFetchingEnvironment
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.collections.HashSet

@RedirectPropertyClass
class NodeSet<T : Node>(value: Collection<T>?) : AbstractSet<T>(), MutableSet<T> {

    private val cache = IdentityHashMap<QueryOptions, NodeQueryResult<T>>()
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

    fun getFromGraphQL(
        @GraphQLIgnore @Autowired
        queryParser: QueryParser,
        dfe: DataFetchingEnvironment
    ): Connection<T> {
        TODO("Implement, add correct types, ...")
    }

    internal fun registerQueryResult(nodeQueryResult: NodeQueryResult<T>) {
        cache[nodeQueryResult.options] = nodeQueryResult
        if (!isLoaded && nodeQueryResult.options.isAllQuery) {
            setCurrentNodes(nodeQueryResult.nodes)
        }
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

    private fun getCurrentNodes(): MutableSet<T> {
        if (isLoaded) {
            return currentNodes!!
        } else {
            TODO()
        }
    }

    //region list implementation

    override val size get() = getCurrentNodes().size

    override fun contains(element: T) = getCurrentNodes().contains(element)

    override fun add(element: T): Boolean {
        val currentNodes = getCurrentNodes()
        val result = currentNodes.add(element)
        if (result) {
            if (!removedNodes.remove(element)) {
                addedNodes.add(element)
            }
        }
        return result
    }

    override fun remove(element: T): Boolean {
        val currentNodes = getCurrentNodes()
        val result = currentNodes.remove(element)
        if (result) {
            if (!addedNodes.remove(element)) {
                removedNodes.add(element)
            }
        }
        return result
    }

    override fun iterator(): MutableIterator<T> {
        return NodeSetIterator(getCurrentNodes().iterator())
    }

    inner class NodeSetIterator(private val parentIterator: MutableIterator<T>): MutableIterator<T> {
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

    //endregion
}
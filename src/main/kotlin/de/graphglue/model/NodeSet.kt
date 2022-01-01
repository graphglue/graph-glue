package de.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import de.graphglue.graphql.execution.NodeQuery
import de.graphglue.graphql.execution.NodeQueryPart
import de.graphglue.graphql.execution.QueryOptions
import de.graphglue.graphql.execution.QueryParser
import de.graphglue.graphql.extensions.getParentNodeDefinition
import de.graphglue.graphql.redirect.RedirectPropertyClass
import de.graphglue.neo4j.execution.NodeQueryResult
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.reflect.KProperty1

@RedirectPropertyClass
class NodeSet<T : Node>(
    value: Collection<T>?,
    private val parent: Node,
    val property: KProperty1<*, *>
) : AbstractSet<T>(), MutableSet<T> {

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

    suspend fun getFromGraphQL(
        @GraphQLIgnore @Autowired
        queryParser: QueryParser,
        dataFetchingEnvironment: DataFetchingEnvironment
    ): DataFetcherResult<Connection<T>> {
        val parentNodeQueryPart = dataFetchingEnvironment.getLocalContext<NodeQueryPart>()
        val result: NodeQueryResult<T>
        val localContext: NodeQuery?
        if (parentNodeQueryPart != null) {
            val providedNodeQuery =
                parentNodeQueryPart.getSubQuery(dataFetchingEnvironment.executionStepInfo.resultKey) {
                    dataFetchingEnvironment.getParentNodeDefinition(queryParser.nodeDefinitionCollection)
                }.query
            val options = providedNodeQuery.options
            result = cache[options] ?: throw IllegalStateException("Result not found in cache")
            localContext = providedNodeQuery
        } else {
            val (loadResult, nodeQuery) = parent.loadNodesOfRelationship<T>(property, dataFetchingEnvironment)
            result = loadResult
            localContext = nodeQuery
        }
        val connection = Connection.fromQueryResult(result, queryParser.objectMapper)
        return DataFetcherResult.newResult<Connection<T>>()
            .data(connection)
            .localContext(localContext)
            .build()
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

    private suspend fun getCurrentNodes(): MutableSet<T> {
        if (!isLoaded) {
            val (result, _) = parent.loadNodesOfRelationship<T>(property)
            currentNodes = result.nodes.toMutableSet()
        }
        return currentNodes!!
    }

    //region list implementation

    override val size get() = runBlocking { getCurrentNodes().size }

    override fun contains(element: T) =  runBlocking { getCurrentNodes().contains(element) }

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

    //endregion
}
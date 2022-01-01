package de.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import de.graphglue.graphql.execution.DEFAULT_PART_ID
import de.graphglue.graphql.execution.NodeQuery
import de.graphglue.graphql.execution.NodeQueryPart
import de.graphglue.graphql.execution.QueryParser
import de.graphglue.graphql.extensions.getParentNodeDefinition
import de.graphglue.graphql.redirect.RedirectPropertyDelegateClass
import de.graphglue.graphql.redirect.RedirectPropertyFunction
import de.graphglue.neo4j.execution.NodeQueryResult
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

@RedirectPropertyDelegateClass
class NodeProperty<T : Node?>(value: T? = null, private val parent: Node, private val property: KProperty1<*, *>) {
    private var isLoaded = false
    private var currentNode: T? = null
    private var persistedNode: T? = null

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
            if (property.returnType.isMarkedNullable) {
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
                currentNode = current
            }
        }
    }

    @RedirectPropertyFunction
    suspend fun getFromGraphQL(
        @GraphQLIgnore @Autowired
        queryParser: QueryParser,
        dataFetchingEnvironment: DataFetchingEnvironment
    ): DataFetcherResult<T> {
        val parentNodeQueryPart = dataFetchingEnvironment.getLocalContext<NodeQueryPart>()
        var localContext = if (parentNodeQueryPart != null) {
            parentNodeQueryPart.getSubQuery(dataFetchingEnvironment.executionStepInfo.resultKey) {
                dataFetchingEnvironment.getParentNodeDefinition(queryParser.nodeDefinitionCollection)
            }.query.parts[DEFAULT_PART_ID]
        } else {
            null
        }
        val (result, nodeQuery) = getCurrentNodeInternal(dataFetchingEnvironment)
        if (localContext == null && nodeQuery != null) {
            localContext = nodeQuery.parts[DEFAULT_PART_ID]
        }
        return DataFetcherResult.newResult<T>()
            .data(result)
            .localContext(localContext)
            .build()
    }

    private suspend fun getCurrentNode(): T? {
        return getCurrentNodeInternal().first
    }

    private suspend fun getCurrentNodeInternal(dataFetchingEnvironment: DataFetchingEnvironment? = null): Pair<T?, NodeQuery?> {
        return if (!isLoaded) {
            val (result, nodeQuery) = parent.loadNodesOfRelationship<T>(property)
            currentNode = result.nodes.first()
            currentNode to nodeQuery
        } else {
            currentNode to null
        }
    }

    internal fun registerQueryResult(nodeQueryResult: NodeQueryResult<T>) {
        if (!isLoaded) {
            if (nodeQueryResult.nodes.size > 1) {
                throw IllegalArgumentException("Too many  nodes for one side of relation")
            }
            currentNode = nodeQueryResult.nodes.firstOrNull()
            persistedNode = currentNode
            isLoaded = true
        }
    }

    internal fun setFromRemote(value: T) {
        if (!isLoaded) {
            currentNode = value
            persistedNode = value
            isLoaded = true
        }
    }
}
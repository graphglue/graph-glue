package de.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import de.graphglue.graphql.extensions.getParentNodeDefinition
import de.graphglue.graphql.redirect.RedirectPropertyDelegateClass
import de.graphglue.graphql.redirect.RedirectPropertyFunction
import de.graphglue.neo4j.execution.*
import de.graphglue.neo4j.repositories.RelationshipDiff
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.runBlocking
import org.neo4j.cypherdsl.core.Cypher
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

@RedirectPropertyDelegateClass
class NodeProperty<T : Node?>(value: T? = null, private val parent: Node, private val property: KProperty1<*, *>) {
    private var isLoaded = false
    private var currentNode: T? = null
    private var persistedNode: T? = null
    private val cache = IdentityHashMap<NodeQueryOptions, NodeQueryResult<T>>()

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

    @RedirectPropertyFunction
    suspend fun getFromGraphQL(
        @GraphQLIgnore @Autowired
        nodeQueryParser: NodeQueryParser,
        dataFetchingEnvironment: DataFetchingEnvironment
    ): DataFetcherResult<T> {
        val parentNodeQueryPart = dataFetchingEnvironment.getLocalContext<NodeQueryPart>()
        val result: NodeQueryResult<T>
        val localContext = if (parentNodeQueryPart != null) {
            val providedNodeQuery =
                parentNodeQueryPart.getSubQuery(dataFetchingEnvironment.executionStepInfo.resultKey) {
                    dataFetchingEnvironment.getParentNodeDefinition(nodeQueryParser.nodeDefinitionCollection)
                }.query
            val options = providedNodeQuery.options
            result = cache[options] ?: throw IllegalStateException("Result not found in cache")
            providedNodeQuery
        } else {
            val (loadResult, nodeQuery) = parent.loadNodesOfRelationship<T>(property, dataFetchingEnvironment)
            result = loadResult
            nodeQuery
        }

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

    internal fun registerQueryResult(nodeQueryResult: NodeQueryResult<T>) {
        cache[nodeQueryResult.options] = nodeQueryResult
        if (!isLoaded && nodeQueryResult.options.isAllQuery) {
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

    internal fun getRelationshipDiff(nodeIdLookup: Map<Node, String>): RelationshipDiff {
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

    internal fun ensureValidSaveState() {
        if (!supportsNull) {
            val neverSetInitialRelationship = currentNode == null && parent.rawId == null
            val removedRequiredRelationship = currentNode == null && persistedNode != null
            if (neverSetInitialRelationship || removedRequiredRelationship) {
                throw IllegalStateException("Non-nullable property $property cannot be saved, as it has value null")
            }
        }
    }

    internal fun getRelatedNodesToSave(): Collection<Node> {
        val current = currentNode
        return if (current != persistedNode && current != null) {
            listOf(current)
        } else {
            emptyList()
        }
    }
}
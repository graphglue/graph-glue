package de.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import de.graphglue.graphql.extensions.getParentNodeDefinition
import de.graphglue.neo4j.execution.*
import de.graphglue.neo4j.repositories.RelationshipDiff
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.reflect.KProperty1

abstract class BaseProperty<T : Node?>(protected val parent: Node, protected val property: KProperty1<*, *>) {

    private val cache = IdentityHashMap<NodeQueryOptions, NodeQueryResult<T>>()

    suspend fun getFromGraphQL(
        @Autowired @GraphQLIgnore
        nodeQueryParser: NodeQueryParser,
        dataFetchingEnvironment: DataFetchingEnvironment
    ): DataFetcherResult<*> {
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
        return constructGraphQLResult(result, localContext, nodeQueryParser)
    }

    internal open fun registerQueryResult(nodeQueryResult: NodeQueryResult<T>) {
        cache[nodeQueryResult.options] = nodeQueryResult
    }

    protected abstract fun constructGraphQLResult(
        result: NodeQueryResult<T>,
        localContext: NodeQuery?,
        nodeQueryParser: NodeQueryParser
    ): DataFetcherResult<*>

    internal abstract fun getRelationshipDiff(nodeIdLookup: Map<Node, String>): RelationshipDiff

    internal abstract fun getRelatedNodesToSave(): Collection<Node>
}
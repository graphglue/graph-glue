package io.github.graphglue.data.execution

import io.github.graphglue.GraphglueCoreConfigurationProperties
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext

/**
 * Engine for executing [NodeQuery]s
 *
 * @param configurationProperties the configuration properties, used for getting the maxium cost of a query
 * @param client the neo4j client used to execute the queries
 * @param mappingContext the neo4j mapping context used to map the results
 */
class NodeQueryEngine(
    private val configurationProperties: GraphglueCoreConfigurationProperties,
    private val client: ReactiveNeo4jClient,
    private val mappingContext: Neo4jMappingContext
) {

    /**
     * Executes the given query
     * May perform multiple requests to the database if the query is too complex.
     *
     * @param query the query to execute
     * @return the result of the query
     */
    suspend fun execute(query: NodeQuery): NodeQueryResult<*> {
        val instance = NodeQueryEngineInstance()
        val newQuery = instance.splitNodeQuery(query)
        val executor = NodeQueryExecutor(client, mappingContext)
        val result = executor.execute(newQuery)
        instance.executeAdditionalQueries(executor)
        return result
    }

    /**
     * Executes the given query
     * May perform multiple requests to the database if the query is too complex.
     *
     * @param query the query to execute
     * @return the result of the query
     */
    suspend fun execute(query: SearchQuery): SearchQueryResult<*> {
        val instance = NodeQueryEngineInstance()
        val newQuery = instance.splitNodeQuery(query)
        val executor = NodeQueryExecutor(client, mappingContext)
        val result = executor.execute(newQuery)
        instance.executeAdditionalQueries(executor)
        return result
    }

    /**
     * Instance of the query engine, used for executing a single query
     */
    private inner class NodeQueryEngineInstance {

        /**
         * Map of the additional queries that need to be executed
         */
        val additionalQueries = mutableMapOf<QueryBase<*>, List<PartialNodeQuery>>()

        /**
         * Splits up a query into multiple smaller queries if necessary
         * Additional queries are stored in [additionalQueries]
         *
         * @param query the query to split up
         * @return the initial query if it is small enough, otherwise a query that can be executed
         */
        fun <T : QueryBase<T>> splitNodeQuery(query: T): T {
            if (query.cost <= configurationProperties.maxQueryCost) {
                return query
            }
            val parts = query.parts.entries.flatMap { (key, part) ->
                splitNodeQueryPart(part).map { key to it }
            }
            val groupedParts = mutableListOf<Map<String, NodeQueryPart>>()
            var currentPart = mutableMapOf<String, NodeQueryPart>()
            for ((key, subPart) in parts) {
                if (currentPart.values.sumOf { it.cost } + subPart.cost > configurationProperties.maxQueryCost || key in currentPart) {
                    groupedParts.add(currentPart)
                    currentPart = mutableMapOf()
                }
                currentPart[key] = subPart
            }
            if (currentPart.isNotEmpty()) {
                groupedParts.add(currentPart)
            }
            val extensionFieldParts = query.parts.filterValues { it.extensionFields.entries.isNotEmpty() }
                .mapValues { NodeQueryPart(emptyList(), it.value.extensionFields.entries) }
            val initialQuery = query.copyWithParts(extensionFieldParts)
            val queries = groupedParts.map {
                PartialNodeQuery(query.definition, it)
            }
            if (queries.isNotEmpty()) {
                additionalQueries[initialQuery] = queries
            }
            return initialQuery
        }

        /**
         * Splits up a query part into multiple smaller parts if necessary
         * Does NOT split up extension fields
         *
         * @param part the part to split up
         * @return the initial part if it is small enough, otherwise a list of parts that can be executed
         */
        private fun splitNodeQueryPart(part: NodeQueryPart): List<NodeQueryPart> {
            return if (part.cost > configurationProperties.maxQueryCost) {
                part.subQueries.entries.map {
                    NodeQueryPart(listOf(splitNodeSubQuery(it)), emptyList())
                }
            } else {
                listOf(part)
            }
        }

        /**
         * Splits up a subquery into multiple smaller subqueries
         *
         * @param subQuery the subquery to split up
         * @return the split up subquery
         */
        private fun splitNodeSubQuery(subQuery: NodeSubQuery): NodeSubQuery {
            val newQuery = splitNodeQuery(subQuery.query)
            return NodeSubQuery(
                newQuery, subQuery.onlyOnTypes, subQuery.relationshipDefinition, subQuery.resultKey
            )
        }

        /**
         * Executes additional queries that were split up
         *
         * @param executor defines the nodes which need to be provided to the additional queries
         */
        suspend fun executeAdditionalQueries(executor: NodeQueryExecutor) {
            coroutineScope {
                val executingQueries = mutableListOf<Deferred<*>>()
                for ((nodeQuery, nodes) in executor.returnedNodesByNodeQuery) {
                    if (nodeQuery in additionalQueries) {
                        val queries = additionalQueries.remove(nodeQuery)!!
                        for (query in queries) {
                            val newExecutor = NodeQueryExecutor(client, mappingContext)
                            val affectedNodes = nodes.filter { query.affectsNode(it) }
                            if (affectedNodes.isNotEmpty()) {
                                executingQueries += async {
                                    newExecutor.executePartial(query, affectedNodes)
                                    executeAdditionalQueries(newExecutor)
                                }
                            }
                        }
                    }
                }
                executingQueries.awaitAll()
            }
        }

    }
}
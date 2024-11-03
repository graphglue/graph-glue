package io.github.graphglue.data.execution

import io.github.graphglue.GraphglueCoreConfigurationProperties
import io.github.graphglue.model.Node
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.neo4j.cypherdsl.core.renderer.Renderer
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext

/**
 * Engine for executing [NodeQuery]s
 *
 * @param configurationProperties the configuration properties, used for getting the maximum cost of a query
 * @param client the neo4j client used to execute the queries
 * @param mappingContext the neo4j mapping context used to map the results
 * @param renderer the renderer used to render the queries
 */
class NodeQueryEngine(
    private val configurationProperties: GraphglueCoreConfigurationProperties,
    private val client: ReactiveNeo4jClient,
    private val mappingContext: Neo4jMappingContext,
    private val renderer: Renderer
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
        val executor = NodeQueryExecutor(client, mappingContext, renderer)
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
        val executor = NodeQueryExecutor(client, mappingContext, renderer)
        val result = executor.execute(newQuery)
        instance.executeAdditionalQueries(executor)
        return result
    }

    /**
     * Executes the given query
     * May perform multiple requests to the database if the query is too complex.
     * Does not provide any results, instead results are directly registered in the affected [nodes]
     *
     * @param query the query to execute
     * @param nodes the nodes for which the subqueries should be executed
     */
    suspend fun execute(query: PartialNodeQuery, nodes: Collection<Node>) {
        val instance = NodeQueryEngineInstance()
        val newQuery = instance.splitNodeQuery(query)
        val executor = NodeQueryExecutor(client, mappingContext, renderer)
        executor.executePartial(newQuery, nodes)
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
            val groupedEntries = mutableListOf<List<NodeQueryEntry<*>>>()
            var currentEntries = mutableListOf<NodeQueryEntry<*>>()
            var currentCost = 0
            for (entry in query.entries) {
                val splitEntry = splitNodeQueryEntry(entry)
                val entryCost = splitEntry.cost
                if (currentCost + entryCost >= configurationProperties.maxQueryCost) {
                    groupedEntries.add(currentEntries)
                    currentEntries = mutableListOf()
                    currentCost = 0
                }
                currentEntries.add(splitEntry)
                currentCost += entryCost
            }
            if (currentEntries.isNotEmpty()) {
                groupedEntries.add(currentEntries)
            }
            val initialQuery = query.copyWithEntries(groupedEntries.firstOrNull() ?: emptyList())
            val queries = groupedEntries.drop(1).map {
                PartialNodeQuery(query.definition, it)
            }
            if (queries.isNotEmpty()) {
                additionalQueries[initialQuery] = queries
            }
            return initialQuery
        }

        /**
         * Splits up a node query entry into multiple smaller entries if possible
         *
         * @param entry the entry to split
         * @return the split up subquery
         */
        private fun splitNodeQueryEntry(entry: NodeQueryEntry<*>): NodeQueryEntry<*> {
            if (entry !is NodeSubQuery || entry.cost <= configurationProperties.maxQueryCost) {
                return entry
            }
            val newQuery = splitNodeQuery(entry.query)
            return NodeSubQuery(
                entry.fieldDefinition,
                newQuery,
                entry.onlyOnTypes,
                entry.relationshipDefinitions,
                entry.resultKeyPath
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
                            val newExecutor = NodeQueryExecutor(client, mappingContext, renderer)
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
package io.github.graphglue.model.property

import io.github.graphglue.data.execution.*
import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.model.Node
import kotlin.reflect.KProperty1

/**
 * Provided as receiver to lambdas which specify which nested properties to load
 *
 * @param T the type of the node to load
 * @property nodeDefinitionCollection the collection of node definitions
 */
class LazyLoadingSubqueryGenerator<T : Node?>(val nodeDefinitionCollection: NodeDefinitionCollection) {

    /**
     * Subqueries to execute
     */
    val subQueries = mutableListOf<NodeSubQuery>()

    /**
     * Load a specific node set property
     *
     * @param property the property to load
     * @param subGenerator specifies which nested properties to load
     */
    @JvmName("loadNodeSet")
    inline fun <reified S : T & Any, reified V : Node> load(
        property: KProperty1<S, LazyLoadingDelegate<V, NodeSetPropertyDelegate<V>.NodeSetProperty>>,
        noinline subGenerator: (LazyLoadingSubqueryGenerator<V>.() -> Unit)? = null
    ) {
        val generator = LazyLoadingSubqueryGenerator<V>(nodeDefinitionCollection)
        subGenerator?.invoke(generator)
        val parentNodeDefinition = nodeDefinitionCollection.getNodeDefinition(S::class)
        val nodeDefinition = nodeDefinitionCollection.getNodeDefinition(V::class)
        val query = NodeQuery(
            nodeDefinition,
            NodeQueryOptions(orderBy = null, fetchTotalCount = false, overrideIsAllQuery = true),
            generator.toQueryParts()
        )
        subQueries += NodeSubQuery(
            query, listOf(parentNodeDefinition), parentNodeDefinition.getRelationshipDefinitionOfProperty(property), ""
        )
    }

    /**
     * Load a specific node property
     *
     * @param property the property to load
     * @param subGenerator specifies which nested properties to load
     */
    @JvmName("loadNode")
    inline fun <reified S : T & Any, reified V : Node> load(
        property: KProperty1<S, LazyLoadingDelegate<V, NodePropertyDelegate<V>.NodeProperty>>,
        noinline subGenerator: (LazyLoadingSubqueryGenerator<V>.() -> Unit)? = null
    ) {
        val generator = LazyLoadingSubqueryGenerator<V>(nodeDefinitionCollection)
        subGenerator?.invoke(generator)
        val parentNodeDefinition = nodeDefinitionCollection.getNodeDefinition(S::class)
        val nodeDefinition = nodeDefinitionCollection.getNodeDefinition(V::class)
        val query = NodeQuery(
            nodeDefinition,
            NodeQueryOptions(orderBy = null, fetchTotalCount = false, first = ONE_NODE_QUERY_LIMIT, overrideIsAllQuery = true),
            generator.toQueryParts()
        )
        subQueries += NodeSubQuery(
            query, listOf(parentNodeDefinition), parentNodeDefinition.getRelationshipDefinitionOfProperty(property), ""
        )
    }

    /**
     * Converts the subqueries to query parts
     *
     * @return the query parts
     */
    fun toQueryParts(): Map<String, NodeQueryPart> {
        return mapOf(DEFAULT_PART_ID to NodeQueryPart(subQueries, emptyList()))
    }

}
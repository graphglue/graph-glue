package io.github.graphglue.model.property

import io.github.graphglue.data.execution.*
import io.github.graphglue.definition.*
import io.github.graphglue.model.Node
import kotlin.reflect.KProperty1

/**
 * Provided as receiver to lambdas which specify which nested properties to load
 *
 * @param T the type of the node to load
 * @param fieldDefinition definition of the currently loaded field
 * @param parentNodeDefinition the definition of the context node
 * @param nodeDefinitionCollection the collection of node definitions
 */
class LazyLoadingSubqueryGenerator<T : Node?>(
    val fieldDefinition: RelationshipFieldDefinition<*>,
    val parentNodeDefinition: NodeDefinition,
    val nodeDefinitionCollection: NodeDefinitionCollection
) {

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
    inline fun <reified S : T & Any, V : Node> load(
        property: KProperty1<S, LazyLoadingDelegate<V, NodeSetPropertyDelegate<V>.NodeSetProperty>>,
        noinline subGenerator: (LazyLoadingSubqueryGenerator<V>.() -> Unit)? = null
    ) {
        val parentNodeDefinition = nodeDefinitionCollection.getNodeDefinition(S::class)
        val fieldDefinition = parentNodeDefinition.getFieldDefinitionOfProperty(property)
        val generator = LazyLoadingSubqueryGenerator<V>(
            fieldDefinition as ManyRelationshipFieldDefinition, parentNodeDefinition, nodeDefinitionCollection
        )
        subGenerator?.invoke(generator)
        subQueries += generator.toSubQuery()
    }

    /**
     * Load a specific node property
     *
     * @param property the property to load
     * @param subGenerator specifies which nested properties to load
     */
    @JvmName("loadNode")
    inline fun <reified S : T & Any, V : Node?> load(
        property: KProperty1<S, LazyLoadingDelegate<V, NodePropertyDelegate<V>.NodeProperty>>,
        noinline subGenerator: (LazyLoadingSubqueryGenerator<V>.() -> Unit)? = null
    ) {
        val parentNodeDefinition = nodeDefinitionCollection.getNodeDefinition(S::class)
        val fieldDefinition = parentNodeDefinition.getFieldDefinitionOfProperty(property)
        val generator = LazyLoadingSubqueryGenerator<V>(
            fieldDefinition as OneRelationshipFieldDefinition, parentNodeDefinition, nodeDefinitionCollection
        )
        subGenerator?.invoke(generator)
        subQueries += generator.toSubQuery()
    }

    /**
     * Creates a [NodeSubQuery] based of the registered [subQueries]
     *
     * @return the created subquery
     */
    fun toSubQuery(): NodeSubQuery {
        val relationshipDefinition = fieldDefinition.relationshipDefinition
        val nodeDefinition = nodeDefinitionCollection.getNodeDefinition(relationshipDefinition.nodeKClass)
        return NodeSubQuery(
            fieldDefinition,
            NodeQuery(
                nodeDefinition, NodeQueryOptions(
                    orderBy = null,
                    fetchTotalCount = false,
                    overrideIsAllQuery = true,
                    first = if (fieldDefinition is OneRelationshipFieldDefinition) ONE_NODE_QUERY_LIMIT else null
                ), subQueries
            ),
            listOf(parentNodeDefinition),
            listOf(AuthorizedRelationDefinition(relationshipDefinition, nodeDefinition, null)),
            ""
        )
    }

}
package io.github.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.generator.annotations.GraphQLName
import com.expediagroup.graphql.generator.scalars.ID
import graphql.schema.DataFetchingEnvironment
import io.github.graphglue.data.LazyLoadingContext
import io.github.graphglue.data.execution.NodeQuery
import io.github.graphglue.data.execution.NodeQueryOptions
import io.github.graphglue.data.execution.NodeQueryResult
import io.github.graphglue.graphql.extensions.requiredPermission
import io.github.graphglue.model.property.BasePropertyDelegate
import io.github.graphglue.model.property.NodePropertyDelegate
import io.github.graphglue.model.property.NodeSetPropertyDelegate
import org.springframework.data.annotation.Transient
import org.springframework.data.neo4j.core.schema.GeneratedValue
import org.springframework.data.neo4j.core.schema.Id
import kotlin.properties.PropertyDelegateProvider
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

/**
 * Name of the bean used as id generator for [Node]s
 */
const val NODE_ID_GENERATOR_BEAN = "nodeIdGenerator"

/**
 * Base class for all Nodes
 * This is always added to the schema
 * All domain entities which can be retrieved via the api
 * and should be persisted in the database should inherit from this class
 * Two nodes are equal iff they have the same id, or, if no id is present yet on both, if they are the same object.
 */
@DomainNode
@AdditionalFilter("idIdFilter")
@GraphQLDescription("Base class of all nodes")
abstract class Node {

    /**
     * Id of this node, `null` if not persisted in the database yet
     */
    @Id
    @GeneratedValue(generatorRef = NODE_ID_GENERATOR_BEAN)
    internal var id: String? = null

    /**
     * Readonly wrapper for the id
     * If `null`, the node has not been persisted in the database yet
     */
    @GraphQLIgnore
    val rawId
        get() = id

    /**
     * The id of the node as seen in the GraphQL API
     * @throws Exception if this node has not been persisted yet and therefore has no id
     */
    @GraphQLName("id")
    @GraphQLDescription("The unique id of this node")
    val graphQLId: ID
        get() = ID(id!!)

    /**
     * Context necessary for lazy-loading
     */
    @Transient
    internal var lazyLoadingContext: LazyLoadingContext? = null

    /**
     * Lookup for all node properties
     * Trades memory (additional HashMap) for a cleaner and more extensible way to lookup the delegates
     * (compared to reflection)
     * Name of property as key
     */
    @Transient
    internal val propertyLookup: MutableMap<String, BasePropertyDelegate<*, *>> = mutableMapOf()

    /**
     * Cached fetched values for extension fields by resultKey
     * Caution: null values should be omitted
     */
    @Transient
    internal var extensionFields: MutableMap<String, Any>? = null

    /**
     * Order fields for sorting and cursor generation fetched from the database
     */
    @Transient
    internal var orderFields: MutableMap<String, Any?>? = null

    /**
     * Creates a new node property used for many sides
     *
     * @param T value type
     * @return a provider for the property delegate
     */
    protected fun <T : Node> NodeSetProperty(): PropertyDelegateProvider<Node, NodeSetPropertyDelegate<T>> {
        return NodeSetPropertyProvider()
    }

    /**
     * Creates a new node property used for one sides
     *
     * @param T value type
     * @return a provider for the property delegate
     */
    protected fun <T : Node?> NodeProperty(): PropertyDelegateProvider<Node, NodePropertyDelegate<T>> {
        return NodePropertyProvider()
    }

    /**
     * Loads all nodes of a relationship
     * If the `dataFetchingEnvironment` is provided, required nested nodes are loaded too
     *
     * @param property defines the relation to load the nodes of
     * @param dataFetchingEnvironment if provided used to define nested nodes to load
     * @return the result of the query and the query itself
     */
    internal suspend fun <T : Node?> loadNodesOfRelationship(
        property: KProperty1<*, *>,
        dataFetchingEnvironment: DataFetchingEnvironment? = null
    ): Pair<NodeQueryResult<T>, NodeQuery?> {
        val lazyLoadingContext = lazyLoadingContext
        if (lazyLoadingContext == null) {
            return NodeQueryResult<T>(NodeQueryOptions(), emptyList(), null) to null
        } else {
            val queryParser = lazyLoadingContext.nodeQueryParser
            val parentNodeDefinition = queryParser.nodeDefinitionCollection.getNodeDefinition(this::class)
            val relationshipDefinition = parentNodeDefinition.getRelationshipDefinitionOfProperty(property)
            val nodeDefinition = queryParser.nodeDefinitionCollection.getNodeDefinition(
                relationshipDefinition.nodeKClass
            )
            val query = queryParser.generateRelationshipNodeQuery(
                nodeDefinition,
                parentNodeDefinition,
                dataFetchingEnvironment,
                relationshipDefinition,
                this,
                dataFetchingEnvironment?.requiredPermission
            )
            @Suppress("UNCHECKED_CAST")
            return lazyLoadingContext.nodeQueryEngine.execute(query) as NodeQueryResult<T> to query
        }
    }

    /**
     * Gets a node property from the lookup
     * May be changed in future to support extensibility
     *
     * @param property the property to lookup
     * @return the found property
     */
    @Suppress("UNCHECKED_CAST")
    internal fun <T : Node?> getProperty(property: KProperty<*>): BasePropertyDelegate<T, *> {
        return propertyLookup[property.name]!! as BasePropertyDelegate<T, *>
    }

    final override fun equals(other: Any?): Boolean {
        return if (other !is Node) {
            false
        } else if (this === other) {
            true
        } else {
            this.rawId != null && this.rawId == other.rawId
        }
    }

    final override fun hashCode(): Int {
        return if (rawId != null) {
            rawId.hashCode()
        } else {
            super.hashCode()
        }
    }

    override fun toString(): String {
        return "${this::class.simpleName}(id=$id)"
    }
}

/**
 * Provider for [NodePropertyDelegate]s
 */
private class NodePropertyProvider<T : Node?> : PropertyDelegateProvider<Node, NodePropertyDelegate<T>> {

    /**
     * Creates a new [NodePropertyDelegate] and registers it to the [Node.propertyLookup]
     *
     * @param thisRef the parent node
     * @param property the property to delegate
     * @return the generated property delegate
     */
    override operator fun provideDelegate(thisRef: Node, property: KProperty<*>): NodePropertyDelegate<T> {
        val nodeProperty = NodePropertyDelegate<T>(
            thisRef,
            property as KProperty1<*, *>
        )
        thisRef.propertyLookup[property.name] = nodeProperty
        return nodeProperty
    }
}

/**
 * Provider for [NodeSetPropertyDelegate]s
 */
private class NodeSetPropertyProvider<T : Node> : PropertyDelegateProvider<Node, NodeSetPropertyDelegate<T>> {

    /**
     * Creates a new [NodeSetPropertyDelegate] and registers it to the [Node.propertyLookup]
     *
     * @param thisRef the parent node
     * @param property the property to delegate
     * @return the generated property delegate
     */
    override operator fun provideDelegate(thisRef: Node, property: KProperty<*>): NodeSetPropertyDelegate<T> {
        val nodeSetPropertyDelegate = NodeSetPropertyDelegate<T>(
            thisRef,
            property as KProperty1<*, *>
        )
        thisRef.propertyLookup[property.name] = nodeSetPropertyDelegate
        return nodeSetPropertyDelegate
    }
}
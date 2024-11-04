package io.github.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.generator.annotations.GraphQLName
import com.expediagroup.graphql.generator.scalars.ID
import com.fasterxml.jackson.databind.ObjectMapper
import graphql.schema.DataFetchingEnvironment
import io.github.graphglue.data.LazyLoadingContext
import io.github.graphglue.data.execution.FieldFetchingContext
import io.github.graphglue.data.execution.NodeQueryEntry
import io.github.graphglue.data.execution.NodeQueryParser
import io.github.graphglue.data.execution.PartialNodeQuery
import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.definition.RelationshipFieldDefinition
import io.github.graphglue.graphql.schema.FieldDataFetchingEnvironment
import io.github.graphglue.model.property.LazyLoadingSubqueryGenerator
import io.github.graphglue.model.property.NodePropertyDelegate
import io.github.graphglue.model.property.NodeSetPropertyDelegate
import io.github.graphglue.model.property.PropertyDelegate
import org.springframework.beans.factory.annotation.Autowired
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
     * True if this node is persisted in the database
     */
    @GraphQLIgnore
    val isPersisted: Boolean
        get() = id != null

    /**
     * Lookup for all node properties
     * Trades memory (additional HashMap) for a cleaner and more extensible way to lookup the delegates
     * (compared to reflection)
     * Name of property as key
     */
    @Transient
    internal val propertyLookup: MutableMap<String, PropertyDelegate<*>> = mutableMapOf()

    /**
     * Cached fetched values for fields
     * Key is the path to the resultKey
     * If entry is not found, it has not been fetched yet
     */
    @Transient
    internal var fieldCache: MutableMap<String, Any?> = mutableMapOf()

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
     * Gets the result of a GraphQL query
     * Uses the cache to obtain the result, and if no cache entry was found, creates a
     * new database query and executes it
     *
     * @param nodeQueryParser used to obtain the [NodeDefinitionCollection] and [ObjectMapper]
     * @param dataFetchingEnvironment environment to fetch data, used to parse subtree of fetched nodes
     * @return the result, including a new local context
     */
    internal suspend fun getFromGraphQL(
        @Autowired
        @GraphQLIgnore
        nodeQueryParser: NodeQueryParser,
        dataFetchingEnvironment: DataFetchingEnvironment,
    ): Any? {
        val fieldDefinition = (dataFetchingEnvironment as FieldDataFetchingEnvironment).fieldDefinition
        val cacheKey = dataFetchingEnvironment.executionStepInfo.path.keysOnly.joinToString("/")
        val lazyLoadingContext = requireNotNull(this.lazyLoadingContext) {
            "Cannot lazy-load field on not-loaded node"
        }
        if (cacheKey !in fieldCache) {
            val nodeDefinition = lazyLoadingContext.nodeDefinitionCollection.getNodeDefinition(this::class)
            val query = PartialNodeQuery(
                nodeDefinition, listOf(
                    fieldDefinition.createQueryEntry(
                        dataFetchingEnvironment,
                        FieldFetchingContext.from(dataFetchingEnvironment),
                        nodeQueryParser,
                        null
                    )
                )
            )
            lazyLoadingContext.nodeQueryEngine.execute(query, listOf(this))
        }
        if (cacheKey !in fieldCache) {
            error("Field $cacheKey not loaded")
        }
        return fieldDefinition.createGraphQLResult(fieldCache[cacheKey], lazyLoadingContext)
    }

    /**
     * Registers the result of a query
     * Used to cache the result of a query
     *
     * @param entry the entry of the query
     * @param queryResult the result of the query
     */
    internal fun registerQueryResult(entry: NodeQueryEntry<*>, queryResult: Any?) {
        fieldCache[entry.resultKeyPath] = queryResult
        if (entry.fieldDefinition.property != null) {
            val property = entry.fieldDefinition.property
            getProperty<PropertyDelegate<Any?>>(property).registerQueryResult(
                entry.fieldDefinition, queryResult
            )
        }
    }

    /**
     * Loads all nodes of a relationship
     * If the `loader` is provided, specified nested nodes are loaded too
     *
     * @param property defines the relation to load the nodes of
     * @param loader if provided used to define nested nodes to load
     * @return the result of the query and the query itself
     */
    internal suspend fun <T : Node?> loadNodesOfRelationship(
        property: KProperty1<*, *>, loader: (LazyLoadingSubqueryGenerator<T>.() -> Unit)?
    ) {
        val lazyLoadingContext = lazyLoadingContext
        if (lazyLoadingContext != null) {
            val nodeDefinitionCollection = lazyLoadingContext.nodeDefinitionCollection
            val parentNodeDefinition = nodeDefinitionCollection.getNodeDefinition(this::class)
            val fieldDefinition =
                parentNodeDefinition.getFieldDefinitionOfProperty(property) as RelationshipFieldDefinition<*>
            val generator =
                LazyLoadingSubqueryGenerator<T>(fieldDefinition, parentNodeDefinition, nodeDefinitionCollection)
            loader?.invoke(generator)
            val query = PartialNodeQuery(parentNodeDefinition, listOf(generator.toSubQuery()))
            lazyLoadingContext.nodeQueryEngine.execute(query, listOf(this))
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
    internal fun <T : PropertyDelegate<*>> getProperty(property: KProperty1<*, *>): T {
        return propertyLookup[property.name]!! as T
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
            thisRef, property as KProperty1<*, *>
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
            thisRef, property as KProperty1<*, *>
        )
        thisRef.propertyLookup[property.name] = nodeSetPropertyDelegate
        return nodeSetPropertyDelegate
    }
}
package io.github.graphglue.model.property

import io.github.graphglue.data.execution.NodeQueryResult
import io.github.graphglue.data.repositories.RelationshipDiff
import io.github.graphglue.definition.*
import io.github.graphglue.model.Node
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

/**
 * Base class for many and one node property delegates
 * Provides cache based GraphQL functionality, and abstract methods with allow persistence
 *
 * @param parent the node which hosts this property
 * @param property the property on the class
 * @param T the type of the value [Node] (s)
 * @param R the type of property
 */
abstract class BaseNodePropertyDelegate<T : Node?, R>(
    parent: Node,
    property: KProperty1<*, *>
) : PropertyDelegate<NodeQueryResult<T & Any>>(parent, property) {

    /**
     * NodeCache used to load this property
     * might be set after initial loading, but must not be changed
     */
    protected var nodeCache: NodeCache? = null

    /**
     * Dynamic type of [parent] and name of [property], can be used for error messages without leaking information
     */
    protected val propertyName = "${parent::class.qualifiedName}.${property.name}"

    /**
     * Delegate for lazy-loading the property
     */
    private val lazyLoadingDelegate = object : LazyLoadingDelegate<T, R> {
        override suspend fun invoke(cache: NodeCache?, loader: (LazyLoadingSubqueryGenerator<T>.() -> Unit)?): R {
            if (nodeCache != null && cache != null && nodeCache != cache) {
                throw IllegalStateException("This property was already loaded with another cache")
            }
            return getLoadedProperty(cache, loader)
        }
    }

    /**
     * Gets the diff of added and removed relationships to persist in the database
     *
     * @return the diff which describes how to add and remove relationships
     */
    internal abstract fun getRelationshipDiff(): RelationshipDiff

    /**
     * Gets [Node]s which should be persisted when this [Node] is persisted
     *
     * @return other nodes to save
     */
    internal abstract fun getRelatedNodesToSave(): Collection<Node>

    /**
     * Gets related nodes defined by this property, but only those already loaded (therefore no lazy loading)
     * The relationships do not have to be persisted yet
     *
     * @return the already loaded related nodes
     */
    internal abstract fun getLoadedRelatedNodes(): Collection<Node>

    /**
     * Gets the loaded property which is returned by the lazy loading delegate
     *
     * @param cache used to load nodes from, if provided, not loading deleted nodes
     * @param loader if provided used to define nested nodes to load
     * @return the loaded property
     */
    internal abstract suspend fun getLoadedProperty(cache: NodeCache?, loader: (LazyLoadingSubqueryGenerator<T>.() -> Unit)?): R

    /**
     * Gets the lazy loading delegate which is used to get the value of the property
     *
     * @param thisRef the [Node] containing the delegated property
     * @param property the delegated property
     * @return the lazy loading delegate which can be used to get the property
     */
    operator fun getValue(thisRef: Node, property: KProperty<*>): LazyLoadingDelegate<T, R> {
        return lazyLoadingDelegate
    }

    /**
     * Called to validate the property
     * Should throw an exception if the property is invalid, with an appropriate reason
     *
     * @param savingNodes the nodes which are currently saved
     * @param relationshipDefinition definition of the relationship
     * @param nodeDefinitionCollection used to obtain the inverse of [relationshipDefinition]
     */
    abstract fun validate(
        savingNodes: Set<Node>,
        relationshipDefinition: RelationshipDefinition,
        nodeDefinitionCollection: NodeDefinitionCollection
    )

    override fun registerQueryResult(
        fieldDefinition: FieldDefinition,
        queryResult: NodeQueryResult<T & Any>
    ) {
        val relationshipDefinition = (fieldDefinition as RelationshipFieldDefinition<*>).relationshipDefinition
        val setter = relationshipDefinition.remotePropertySetter
        if (setter != null) {
            val nodes = queryResult.nodes
            for (node in nodes) {
                setter(node, parent)
            }
        }
    }
}
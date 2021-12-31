package de.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.generator.annotations.GraphQLName
import com.expediagroup.graphql.generator.scalars.ID
import de.graphglue.graphql.execution.NodeQuery
import de.graphglue.graphql.execution.QueryOptions
import de.graphglue.neo4j.LazyLoadingContext
import de.graphglue.neo4j.execution.NodeQueryExecutor
import de.graphglue.neo4j.execution.NodeQueryResult
import graphql.schema.DataFetchingEnvironment
import org.springframework.data.neo4j.core.convert.ConvertWith
import org.springframework.data.neo4j.core.schema.GeneratedValue
import org.springframework.data.neo4j.core.schema.Id
import org.springframework.data.neo4j.core.schema.Property
import org.springframework.data.neo4j.core.support.UUIDStringGenerator
import java.util.*
import kotlin.properties.PropertyDelegateProvider
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

/**
 * Base class for all Nodes
 * This is always added to the schema
 * All domain entities which can be retrieved via the api
 * and should be persisted in the database should inherit from this class
 */
@DomainNode
@AdditionalFilter("idIdFilter")
@GraphQLDescription("Base class of all nodes")
abstract class Node {

    @GraphQLIgnore
    @Id
    @GeneratedValue(UUIDStringGenerator::class)
    internal var id: String? = null

    @GraphQLIgnore
    val rawId get() = id

    @Property("_")
    @ConvertWith(converterRef = "lazyLoadingContextConverter")
    private var lazyLoadingContextOptional: Optional<LazyLoadingContext> = Optional.empty()

    private val lazyLoadingContext: LazyLoadingContext? get() = lazyLoadingContextOptional.orElse(null)

    @GraphQLName("id")
    @GraphQLDescription("The unique id of this node")
    val graphQLId: ID get() = ID(id!!)

    protected fun <T : Node> NodeSetProperty(value: Collection<T>? = null): PropertyDelegateProvider<Node, NodeSetProperty<T>> {
        return NodeSetPropertyProvider(value)
    }

    protected fun <T : Node> NodeProperty(value: T? = null): PropertyDelegateProvider<Node, NodeProperty<T>> {
        return NodePropertyProvider(value)
    }

    internal fun <T : Node?> loadNodesOfRelationship(
        property: KProperty1<*, *>,
        dataFetchingEnvironment: DataFetchingEnvironment? = null
    ): Pair<NodeQueryResult<T>, NodeQuery?> {
        val lazyLoadingContext = lazyLoadingContext
        if (lazyLoadingContext == null) {
            return NodeQueryResult<T>(QueryOptions(), emptyList(), null) to null
        } else {
            val queryParser = lazyLoadingContext.queryParser
            val parentNodeDefinition = queryParser.nodeDefinitionCollection.getNodeDefinition(this::class)
            val relationshipDefinition = parentNodeDefinition.getRelationshipDefinitionOfProperty(property)
            val nodeDefinition =
                queryParser.nodeDefinitionCollection.getNodeDefinition(relationshipDefinition.nodeKClass)
            val query = queryParser.generateRelationshipNodeQuery(
                nodeDefinition,
                dataFetchingEnvironment,
                relationshipDefinition,
                this
            )
            val queryExecutor =
                NodeQueryExecutor(query, lazyLoadingContext.neo4jClient, lazyLoadingContext.neo4jMappingContext)
            @Suppress("UNCHECKED_CAST")
            return queryExecutor.execute() as NodeQueryResult<T> to query
        }
    }
}


private class NodePropertyProvider<T : Node?>(private val value: T?) : PropertyDelegateProvider<Node, NodeProperty<T>> {
    override operator fun provideDelegate(thisRef: Node, property: KProperty<*>) = NodeProperty(
        value, thisRef,
        property as KProperty1<*, *>
    )
}

private class NodeSetPropertyProvider<T : Node>(private val value: Collection<T>?) :
    PropertyDelegateProvider<Node, NodeSetProperty<T>> {
    override operator fun provideDelegate(thisRef: Node, property: KProperty<*>) = NodeSetProperty(
        value, thisRef,
        property as KProperty1<*, *>
    )
}
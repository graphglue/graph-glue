package com.nkcoding.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.generator.scalars.ID
import com.nkcoding.graphglue.graphql.execution.NodeQuery
import com.nkcoding.graphglue.graphql.execution.QueryOptions
import com.nkcoding.graphglue.graphql.execution.QueryParser
import com.nkcoding.graphglue.neo4j.execution.NodeQueryExecutor
import com.nkcoding.graphglue.neo4j.execution.NodeQueryResult
import graphql.schema.DataFetchingEnvironment
import org.springframework.context.ApplicationContext
import org.springframework.data.neo4j.core.Neo4jClient
import org.springframework.data.neo4j.core.convert.ConvertWith
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
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
@Neo4jNode
@GraphQLDescription("Base class of all nodes")
abstract class Node {

    @GraphQLIgnore
    @Id
    @GeneratedValue(UUIDStringGenerator::class)
    internal lateinit var id: String

    @Property("_")
    @ConvertWith(converterRef = "applicationContextConverter")
    private var applicationContextOptional: Optional<ApplicationContext> = Optional.empty()

    private val applicationContext: ApplicationContext? get() = applicationContextOptional.orElse(null)

    @GraphQLDescription("The unique id of this node")
    fun id(): ID {
        return ID(id)
    }

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
        val applicationContext = applicationContext
        if (applicationContext == null) {
            return NodeQueryResult<T>(QueryOptions(), emptyList(), null) to null
        } else {
            val client = applicationContext.getBean(Neo4jClient::class.java)
            val mappingContext = applicationContext.getBean(Neo4jMappingContext::class.java)
            val queryParser = applicationContext.getBean(QueryParser::class.java)
            val parentNodeDefinition = queryParser.nodeDefinitionCollection.backingCollection[this::class]!!
            val relationshipDefinition = parentNodeDefinition.getRelationshipDefinitionOfProperty(property)
            val nodeDefinition =
                queryParser.nodeDefinitionCollection.backingCollection[relationshipDefinition.nodeKClass]!!
            val query = queryParser.generateRelationshipNodeQuery(
                nodeDefinition,
                dataFetchingEnvironment,
                relationshipDefinition,
                this
            )
            val queryExecutor = NodeQueryExecutor(query, client, mappingContext)
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
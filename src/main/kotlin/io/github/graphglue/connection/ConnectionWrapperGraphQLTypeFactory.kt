package io.github.graphglue.connection

import com.expediagroup.graphql.generator.execution.KotlinDataFetcherFactoryProvider
import graphql.Scalars
import graphql.language.EnumValue
import graphql.schema.*
import io.github.graphglue.connection.filter.definition.SubFilterGenerator
import io.github.graphglue.connection.filter.definition.generateFilterDefinition
import io.github.graphglue.connection.order.OrderField
import io.github.graphglue.connection.order.generateOrders
import io.github.graphglue.graphql.extensions.getSimpleName
import io.github.graphglue.model.Connection
import io.github.graphglue.model.Edge
import io.github.graphglue.model.Node
import io.github.graphglue.util.CacheMap
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure

/**
 * Factory for the Connection GraphQL wrapper type
 * Used to build the [GraphQLType] for the connection
 *
 * @param outputTypeCache cache for [GraphQLOutputType]s
 * @param inputTypeCache cache for [GraphQLInputType]s
 * @param subFilterGenerator used to generate filter fields
 * @param codeRegistry used to register [DataFetcherFactory]s
 * @param dataFetcherFactoryProvider used to get function and property data fetchers
 * @param mappingContext mapping context of Neo4j, defines schema of [Node] classes
 */
class ConnectionWrapperGraphQLTypeFactory(
    private val outputTypeCache: CacheMap<String, GraphQLOutputType>,
    private val inputTypeCache: CacheMap<String, GraphQLInputType>,
    private val subFilterGenerator: SubFilterGenerator,
    private val codeRegistry: GraphQLCodeRegistry.Builder,
    private val dataFetcherFactoryProvider: KotlinDataFetcherFactoryProvider,
    private val mappingContext: Neo4jMappingContext,
) {

    /**
     * Generates the GraphQL type for the wrapper connection type
     * This type is never directly used in the schema, but provides the `getFromGraphQL`field
     *
     * @param connectionType the type of the connection, e.g. `Set<T : Node>`
     * @return the generated [GraphQLType]
     */
    fun generateWrapperGraphQLType(connectionType: KType): GraphQLType {
        @Suppress("UNCHECKED_CAST") val returnNodeType =
            connectionType.arguments[0].type!!.jvmErasure as KClass<out Node>
        val returnNodeName = returnNodeType.getSimpleName()
        return generateWrapperGraphQLType(returnNodeType, returnNodeName)
    }

    /**
     * Generates the GraphQL type for the wrapper connection type
     * This type is never directly used in the schema, but provides the `getFromGraphQL`field
     *
     * @param nodeType the type of the [Node] elements of the connection
     * @param nodeName the name of the [Node] type
     * @return the generated [GraphQLType]
     */
    fun generateWrapperGraphQLType(nodeType: KClass<out Node>, nodeName: String): GraphQLOutputType {
        val name = "${nodeName}ListWrapper"
        return outputTypeCache.computeIfAbsent(name, GraphQLTypeReference(name)) {
            val filter = generateFilterDefinition(nodeType, subFilterGenerator)
            val orders = generateOrders(nodeType, mappingContext.getPersistentEntity(nodeType.java)!!)
            val type = GraphQLObjectType.newObject().name(name).field { fieldBuilder ->
                fieldBuilder.name("getFromGraphQL").argument {
                    it.name("filter").description("Filter for specific items in the connection")
                        .type(filter.toGraphQLType(inputTypeCache))
                }.argument {
                    it.name("orderBy").description("Order in which the items are sorted")
                        .type(generateOrderGraphQLType(nodeName, orders))
                }.argument {
                    it.name("after").description("Get only items after the cursor").type(Scalars.GraphQLString)
                }.argument {
                    it.name("before").description("Get only items before the cursor").type(Scalars.GraphQLString)
                }.argument {
                    it.name("first").description("Get the first n items. Must not be used if before is specified")
                        .type(Scalars.GraphQLInt)
                }.argument {
                    it.name("last").description("Get the last n items. Must not be used if after is specified")
                        .type(Scalars.GraphQLInt)
                }.type(GraphQLNonNull(generateConnectionGraphQLType(nodeName)))
            }.build()
            type
        }
    }

    /**
     * Generates the connection type for a specific [Node] type
     * As specified by the connection specification, has a `nodes`, `edges`, `totalCount` and `pageInfo` field
     *
     * @param nodeName the name of the [Node]
     * @return the generated connection [GraphQLOutputType]
     */
    private fun generateConnectionGraphQLType(nodeName: String): GraphQLOutputType {
        val name = "${nodeName}Connection"
        return outputTypeCache.computeIfAbsent(name, GraphQLTypeReference(name)) {
            val type =
                GraphQLObjectType.newObject().name(name).description("The connection type for ${nodeName}.")
                    .field {
                        it.name("nodes").description("A list of all nodes of the current page.")
                            .type(GraphQLNonNull(GraphQLList(GraphQLNonNull(GraphQLTypeReference(nodeName)))))
                    }.field {
                        it.name("edges").description("A list of all edges of the current page.")
                            .type(GraphQLNonNull(GraphQLList(GraphQLNonNull(generateEdgeGraphQLType(nodeName)))))
                    }.field {
                        it.name("totalCount").description("Identifies the total count of items in the connection.")
                            .type(GraphQLNonNull(Scalars.GraphQLInt))
                    }.field {
                        it.name("pageInfo").description("Information to aid in pagination.")
                            .type(GraphQLNonNull(GraphQLTypeReference("PageInfo")))
                    }.build()

            registerFunctionDataFetcher(type, "nodes", Connection::class)
            registerFunctionDataFetcher(type, "edges", Connection::class)
            registerFunctionDataFetcher(type, "totalCount", Connection::class)
            registerPropertyDataFetcher(type, "pageInfo", Connection::class)
            type
        }
    }

    /**
     * Generates the edge type for the `edges` field of the connection.
     * The generated type has a `node` field, which returns the [Node] associated with the edge, and
     * the `cursor` field, which allows getting nodes after / before the edge
     *
     * @param nodeName the name of the [Node]
     * @return the generated type for the edges
     */
    private fun generateEdgeGraphQLType(nodeName: String): GraphQLOutputType {
        val name = "${nodeName}Edge"
        return outputTypeCache.computeIfAbsent(name, GraphQLTypeReference(name)) {
            val type = GraphQLObjectType.newObject().name(name).description("An edge in a connection.").field {
                it.name("node").description("The item at the end of the edge.")
                    .type(GraphQLNonNull(GraphQLTypeReference(nodeName)))
            }.field {
                it.name("cursor").description("A cursor used in pagination.")
                    .type(GraphQLNonNull(Scalars.GraphQLString))
            }.build()

            registerFunctionDataFetcher(type, "node", Edge::class)
            registerFunctionDataFetcher(type, "cursor", Edge::class)
            type
        }
    }

    /**
     * Generates the order type with a `direction` and an `field` field, where field is of the specific enum for the
     * [Node] type
     *
     * @param nodeName the name of the [Node]
     * @param orders the possible [OrderField]s for the [Node] type with the enum name as key
     * @return the generated [GraphQLInputType] used to specific the order in which the nodes of a connection are
     *         returned
     */
    private fun generateOrderGraphQLType(nodeName: String, orders: Map<String, OrderField<*>>): GraphQLInputType {
        val name = "${nodeName}Order"
        return inputTypeCache.computeIfAbsent(name, GraphQLTypeReference(name)) {
            GraphQLInputObjectType.newInputObject().name(name)
                .description("Defines the order of a $nodeName list").field {
                    it.name("field").description("The field to order by, defaults to ID")
                        .type(generateOrderFieldGraphQLType(nodeName, orders))
                        .defaultValueLiteral(EnumValue("ID"))
                }.field {
                    it.name("direction").description("The direction to order by, defaults to ASC")
                        .type(GraphQLTypeReference("OrderDirection")).defaultValueLiteral(EnumValue("ASC"))
                }.build()
        }
    }

    /**
     * Generates the enum which defines all possible order fields
     *
     * @param nodeName the name of the [Node]
     * @param orders the possible [OrderField]s for the [Node] type with the enum name as key
     */
    private fun generateOrderFieldGraphQLType(
        nodeName: String, orders: Map<String, OrderField<*>>
    ): GraphQLInputType {
        val name = "${nodeName}OrderField"
        return inputTypeCache.computeIfAbsent(name, GraphQLTypeReference(name)) {
            val builder =
                GraphQLEnumType.newEnum().name(name).description("Fields a list of $nodeName can be sorted by")
            for ((fieldName, fieldValue) in orders.entries) {
                builder.value(fieldName, fieldValue, "Order by ${fieldValue.name}")
            }
            builder.build()
        }
    }

    /**
     * Registers a data fetcher for a specific property
     *
     * @param type the container type on which to register the data fetcher
     * @param fieldName the name of the property
     * @param kClass the class which contains the property to use for data fetching
     */
    private fun registerPropertyDataFetcher(type: GraphQLFieldsContainer, fieldName: String, kClass: KClass<*>) {
        val property = kClass.memberProperties.first { it.name == fieldName }
        val dataFetcherFactory = dataFetcherFactoryProvider.propertyDataFetcherFactory(kClass, property)
        registerDataFetcher(type, fieldName, dataFetcherFactory)
    }

    /**
     * Registers a data fetcher for a specific function
     *
     * @param type the container type on which to register the data fetcher
     * @param fieldName the name of the function
     * @param kClass the class which contains the function to use for data fetching
     */
    private fun registerFunctionDataFetcher(type: GraphQLFieldsContainer, fieldName: String, kClass: KClass<*>) {
        val function = kClass.memberFunctions.first { it.name == fieldName }
        val dataFetcherFactory = dataFetcherFactoryProvider.functionDataFetcherFactory(null, function)
        registerDataFetcher(type, fieldName, dataFetcherFactory)
    }

    /**
     * Registers a [DataFetcherFactory] for a specific [fieldName] on a specific [GraphQLFieldsContainer]
     *
     * @param type the container for which to register the data fetcher
     * @param fieldName the name of the field for which to register the data fetcher
     * @param dataFetcherFactory the data fetcher to register
     */
    private fun registerDataFetcher(
        type: GraphQLFieldsContainer, fieldName: String, dataFetcherFactory: DataFetcherFactory<Any?>
    ) {
        val fieldCoordinates = FieldCoordinates.coordinates(type, fieldName)
        codeRegistry.dataFetcher(fieldCoordinates, dataFetcherFactory)
    }
}
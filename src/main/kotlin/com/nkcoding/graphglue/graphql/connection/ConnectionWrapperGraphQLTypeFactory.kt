package com.nkcoding.graphglue.graphql.connection

import com.expediagroup.graphql.generator.execution.KotlinDataFetcherFactoryProvider
import com.nkcoding.graphglue.graphql.connection.filter.definition.SubFilterGenerator
import com.nkcoding.graphglue.graphql.connection.filter.definition.generateFilterDefinition
import com.nkcoding.graphglue.graphql.extensions.getSimpleName
import com.nkcoding.graphglue.graphql.redirect.REDIRECT_PROPERTY_DIRECTIVE
import com.nkcoding.graphglue.model.Connection
import com.nkcoding.graphglue.model.Edge
import com.nkcoding.graphglue.model.Node
import graphql.Scalars
import graphql.schema.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure

class ConnectionWrapperGraphQLTypeFactory(
    private val outputTypeCache: MutableMap<String, GraphQLObjectType>,
    private val inputTypeCache: MutableMap<String, GraphQLInputObjectType>,
    private val subFilterGenerator: SubFilterGenerator,
    private val codeRegistry: GraphQLCodeRegistry.Builder,
    private val dataFetcherFactoryProvider: KotlinDataFetcherFactoryProvider
) {


    fun generateWrapperGraphQLType(connectionType: KType): GraphQLType {
        val returnNodeName = connectionType.arguments[0].type!!.jvmErasure.getSimpleName()
        val name = "${returnNodeName}ListWrapper"
        return outputTypeCache.computeIfAbsent(name) {
            @Suppress("UNCHECKED_CAST") val filter = generateFilterDefinition(
                connectionType.arguments[0].type?.jvmErasure as KClass<out Node>, subFilterGenerator
            )

            val type = GraphQLObjectType.newObject().name(name).withDirective(REDIRECT_PROPERTY_DIRECTIVE)
                .field { fieldBuilder ->
                    fieldBuilder.name("getFromGraphQL").withDirective(REDIRECT_PROPERTY_DIRECTIVE).argument {
                        it.name("filter").type(filter.toGraphQLType(inputTypeCache))
                    }.argument {
                        it.name("after").type(Scalars.GraphQLString)
                    }.argument {
                        it.name("before").type(Scalars.GraphQLString)
                    }.argument {
                        it.name("first").type(Scalars.GraphQLInt)
                    }.argument {
                        it.name("last").type(Scalars.GraphQLInt)
                    }.type(GraphQLNonNull(generateConnectionGraphQLType(returnNodeName)))
                }.build()

            type
        }
    }

    private fun generateConnectionGraphQLType(returnNodeName: String): GraphQLOutputType {
        val name = "${returnNodeName}Connection"
        return outputTypeCache.computeIfAbsent(name) {
            val type =
                GraphQLObjectType.newObject().name(name).description("The connection type for ${returnNodeName}.")
                    .field {
                        it.name("nodes").description("A list of all nodes of the current page.")
                            .type(GraphQLNonNull(GraphQLList(GraphQLNonNull(GraphQLTypeReference(returnNodeName)))))
                    }.field {
                        it.name("edges").description("A list of all edges of the current page.").type(
                            GraphQLNonNull(
                                GraphQLList(
                                    GraphQLNonNull(
                                        generateEdgeGraphQLType(
                                            returnNodeName,
                                        )
                                    )
                                )
                            )
                        )
                    }.field {
                        it.name("totalCount").description("Identifies the total count of items in the connection.")
                            .type(GraphQLNonNull(Scalars.GraphQLInt))
                    }.field {
                        it.name("pageInfo").description("Information to aid in pagination.")
                            .type(GraphQLNonNull(GraphQLTypeReference("PageInfo")))
                    }.build()

            registerPropertyDataFetcher(type, "nodes", Connection::class)
            registerFunctionDataFetcher(type, "edges", Connection::class)
            registerPropertyDataFetcher(type, "totalCount", Connection::class)
            registerPropertyDataFetcher(type, "pageInfo", Connection::class)

            type
        }
    }

    private fun generateEdgeGraphQLType(returnNodeName: String): GraphQLOutputType {
        val name = "${returnNodeName}Edge"
        return outputTypeCache.computeIfAbsent(name) {
            val type = GraphQLObjectType.newObject().name(name).description("An edge in a connection.").field {
                it.name("node").description("The item at the end of the edge.")
                    .type(GraphQLNonNull(GraphQLTypeReference(returnNodeName)))
            }.field {
                it.name("cursor").description("A cursor used in pagination.")
                    .type(GraphQLNonNull(Scalars.GraphQLString))
            }.build()

            registerPropertyDataFetcher(type, "node", Edge::class)
            registerFunctionDataFetcher(type, "cursor", Edge::class)

            type
        }
    }

    private fun registerPropertyDataFetcher(type: GraphQLFieldsContainer, fieldName: String, kClass: KClass<*>) {
        val property = kClass.memberProperties.first { it.name == fieldName }
        val dataFetcherFactory = dataFetcherFactoryProvider.propertyDataFetcherFactory(kClass, property)
        registerDataFetcher(type, fieldName, dataFetcherFactory)
    }

    private fun registerFunctionDataFetcher(type: GraphQLFieldsContainer, fieldName: String, kClass: KClass<*>) {
        val function = kClass.memberFunctions.first { it.name == fieldName }
        val dataFetcherFactory = dataFetcherFactoryProvider.functionDataFetcherFactory(kClass, function)
        registerDataFetcher(type, fieldName, dataFetcherFactory)
    }

    private fun registerDataFetcher(
        type: GraphQLFieldsContainer, fieldName: String, dataFetcherFactory: DataFetcherFactory<Any?>
    ) {
        val fieldCoordinates = FieldCoordinates.coordinates(type, fieldName)
        codeRegistry.dataFetcher(fieldCoordinates, dataFetcherFactory)
    }
}
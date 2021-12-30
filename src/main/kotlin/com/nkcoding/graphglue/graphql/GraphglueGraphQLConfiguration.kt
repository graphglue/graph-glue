package com.nkcoding.graphglue.graphql

import com.expediagroup.graphql.generator.SchemaGenerator
import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelNames
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.execution.KotlinDataFetcherFactoryProvider
import com.expediagroup.graphql.generator.extensions.print
import com.expediagroup.graphql.generator.hooks.NoopSchemaGeneratorHooks
import com.expediagroup.graphql.generator.hooks.SchemaGeneratorHooks
import com.expediagroup.graphql.server.operations.Mutation
import com.expediagroup.graphql.server.operations.Query
import com.expediagroup.graphql.server.operations.Subscription
import com.expediagroup.graphql.server.spring.GraphQLConfigurationProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.nkcoding.graphglue.graphql.connection.ConnectionWrapperGraphQLTypeFactory
import com.nkcoding.graphglue.graphql.connection.filter.GraphglueGraphQLFilterConfiguration
import com.nkcoding.graphglue.graphql.connection.filter.TypeFilterDefinitionEntry
import com.nkcoding.graphglue.graphql.connection.filter.definition.*
import com.nkcoding.graphglue.graphql.connection.order.OrderDirection
import com.nkcoding.graphglue.graphql.execution.QueryParser
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinition
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinitionCollection
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinitionCollectionImpl
import com.nkcoding.graphglue.graphql.extensions.getSimpleName
import com.nkcoding.graphglue.graphql.extensions.springFindAnnotation
import com.nkcoding.graphglue.graphql.extensions.toTopLevelObjects
import com.nkcoding.graphglue.graphql.query.GraphglueQuery
import com.nkcoding.graphglue.graphql.query.TopLevelQueryProvider
import com.nkcoding.graphglue.graphql.redirect.RedirectKotlinDataFetcherFactoryProvider
import com.nkcoding.graphglue.graphql.redirect.rewireFieldType
import com.nkcoding.graphglue.model.DomainNode
import com.nkcoding.graphglue.model.Node
import com.nkcoding.graphglue.model.NodeSet
import com.nkcoding.graphglue.model.PageInfo
import graphql.schema.*
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.jvmErasure

/**
 * Configures beans used in combination with graphql-kotlin and graphql-java
 */
@Configuration
@Import(GraphglueGraphQLFilterConfiguration::class)
class GraphglueGraphQLConfiguration(private val neo4jMappingContext: Neo4jMappingContext) {

    private val logger = LoggerFactory.getLogger(GraphglueGraphQLConfiguration::class.java)

    private val inputTypeCache = ConcurrentHashMap<String, GraphQLInputType>()
    private val outputTypeCache = ConcurrentHashMap<String, GraphQLOutputType>()
    private val filterDefinitions: FilterDefinitionCache =
        ConcurrentHashMap<KClass<out Node>, FilterDefinition<out Node>>()
    private val nodeDefinitions = ConcurrentHashMap<KClass<out Node>, NodeDefinition>()
    private val supertypeNodeDefinitionLookup = ConcurrentHashMap<Set<String>, NodeDefinition>()
    private val nodeDefinitionCollection =
        NodeDefinitionCollectionImpl(nodeDefinitions, supertypeNodeDefinitionLookup, neo4jMappingContext)
    private val topLevelQueries = ConcurrentHashMap<NodeDefinition, TopLevelQueryDefinition>()

    /**
     * Code registry used as a temporary cache before its DataFetchers are added to the
     * real code registry
     * must only be used to
     */
    private val tempCodeRegistry: GraphQLCodeRegistry.Builder = GraphQLCodeRegistry.newCodeRegistry()

    /**
     * Provides the [SchemaGeneratorHooks] for the [SchemaGeneratorConfig]
     */
    @Bean
    @ConditionalOnMissingBean
    fun schemaGeneratorHooks(
        factory: ConnectionWrapperGraphQLTypeFactory,
        applicationContext: ApplicationContext,
        objectMapper: ObjectMapper,
        dataFetcherFactoryProvider: KotlinDataFetcherFactoryProvider
    ): SchemaGeneratorHooks {
        return object : SchemaGeneratorHooks {
            override fun onRewireGraphQLType(
                generatedType: GraphQLSchemaElement,
                coordinates: FieldCoordinates?,
                codeRegistry: GraphQLCodeRegistry.Builder
            ): GraphQLSchemaElement {
                codeRegistry.dataFetchers(tempCodeRegistry.build())
                tempCodeRegistry.clearDataFetchers()
                val rewiredType = super.onRewireGraphQLType(generatedType, coordinates, codeRegistry)
                return if (rewiredType is GraphQLFieldDefinition) {
                    rewireFieldType(rewiredType, coordinates, codeRegistry)
                } else {
                    return rewiredType
                }
            }

            override fun willGenerateGraphQLType(type: KType): GraphQLType? {
                if (type.isSubtypeOf(Node::class.createType())) {
                    @Suppress("UNCHECKED_CAST") val nodeClass = type.jvmErasure as KClass<out Node>
                    val nodeDefinition = nodeDefinitionCollection.getOrCreate(nodeClass)
                    val domainNodeAnnotation = nodeClass.springFindAnnotation<DomainNode>()
                    val topLevelFunctionName = domainNodeAnnotation?.topLevelQueryName
                    if (topLevelFunctionName?.isNotBlank() == true) {
                        topLevelQueries[nodeDefinition] = TopLevelQueryDefinition(
                            topLevelFunctionName,
                            factory.generateWrapperGraphQLType(nodeClass, nodeClass.getSimpleName())
                        )
                    }
                }
                return if (type.jvmErasure == NodeSet::class) {
                    factory.generateWrapperGraphQLType(type)
                } else {
                    super.willGenerateGraphQLType(type)
                }
            }

            override fun willBuildSchema(builder: GraphQLSchema.Builder): GraphQLSchema.Builder {
                for ((nodeClass, nodeDefinition) in nodeDefinitions) {
                    val subTypes = nodeDefinitions.keys.filter { it.isSubclassOf(nodeClass) }
                        .filter { !it.isAbstract }
                        .map { it.getSimpleName() }
                        .toSet()
                    supertypeNodeDefinitionLookup[subTypes] = nodeDefinition
                }

                return super.willBuildSchema(completeSchema(builder))
            }

            private fun completeSchema(builder: GraphQLSchema.Builder): GraphQLSchema.Builder {
                val schema = builder.build()
                val codeRegistry = schema.codeRegistry
                val newBuilder = GraphQLSchema.newSchema(builder.build())
                updateQueryType(schema, newBuilder)
                newBuilder.codeRegistry(
                    codeRegistry.transform {
                        it.dataFetchers(tempCodeRegistry.build())
                    }
                )
                return newBuilder
            }

            private fun updateQueryType(schema: GraphQLSchema, newBuilder: GraphQLSchema.Builder) {
                val queryType = schema.queryType!!
                val newQueryType = queryType.transform {
                    val function = TopLevelQueryProvider::class.memberFunctions.first { it.name == "getFromGraphQL" }
                    for ((nodeDefinition, queryDefinition) in topLevelQueries) {
                        val (name, wrapperType) = queryDefinition
                        wrapperType as GraphQLObjectType
                        val field = wrapperType.fields.first().transform { fieldBuilder ->
                            fieldBuilder.name(name)
                                .description("Query for nodes of type ${nodeDefinition.nodeType.getSimpleName()}")
                        }
                        it.field(field)
                        val coordinates = FieldCoordinates.coordinates(queryType.name, name)
                        val dataFetcherFactory = dataFetcherFactoryProvider.functionDataFetcherFactory(
                            TopLevelQueryProvider<Node>(nodeDefinition), function
                        )
                        tempCodeRegistry.dataFetcher(coordinates, dataFetcherFactory)
                    }
                }
                newBuilder.query(newQueryType)
            }
        }
    }

    /**
     * Gets a list of all filter definitions
     */
    @Bean
    @ConditionalOnMissingBean
    fun filterDefinitions() = FilterDefinitionCollection(filterDefinitions)

    /**
     * Gets a list of all node definitions
     */
    @Bean
    @ConditionalOnMissingBean
    fun nodeDefinitions() = nodeDefinitionCollection

    @Bean
    @ConditionalOnMissingBean
    fun queryParser(
        nodeDefinitionCollection: NodeDefinitionCollection,
        filterDefinitionCollection: FilterDefinitionCollection,
        objectMapper: ObjectMapper
    ) = QueryParser(nodeDefinitionCollection, filterDefinitionCollection, objectMapper)

    @Bean
    @ConditionalOnMissingBean
    fun schemaConfig(
        config: GraphQLConfigurationProperties,
        topLevelNames: Optional<TopLevelNames>,
        hooks: Optional<SchemaGeneratorHooks>,
        dataFetcherFactoryProvider: KotlinDataFetcherFactoryProvider
    ): SchemaGeneratorConfig {
        val generatorHooks = hooks.orElse(NoopSchemaGeneratorHooks)
        return SchemaGeneratorConfig(
            supportedPackages = listOf("com.nkcoding.graphglue") + config.packages,
            topLevelNames = topLevelNames.orElse(TopLevelNames()),
            hooks = generatorHooks,
            dataFetcherFactoryProvider = dataFetcherFactoryProvider,
            introspectionEnabled = config.introspection.enabled
        )
    }

    @Bean
    @ConditionalOnMissingBean
    fun schema(
        queries: Optional<List<Query>>,
        mutations: Optional<List<Mutation>>,
        subscriptions: Optional<List<Subscription>>,
        schemaConfig: SchemaGeneratorConfig
    ): GraphQLSchema {
        val generator = SchemaGenerator(schemaConfig)
        val nodeDefinition = nodeDefinitionCollection.getOrCreate(Node::class)
        val schema = generator.use {
            it.generateSchema(
                queries.orElse(emptyList()).toTopLevelObjects() + TopLevelObject(GraphglueQuery(nodeDefinition)),
                mutations.orElse(emptyList()).toTopLevelObjects(),
                subscriptions.orElse(emptyList()).toTopLevelObjects(),
                additionalTypes = setOf(Node::class.createType(), PageInfo::class.createType()),
                additionalInputTypes = setOf(OrderDirection::class.createType()),
            )
        }

        logger.info("\n${schema.print()}")
        return schema
    }

    @Bean
    @ConditionalOnMissingBean
    fun dataFetcherFactoryProvider(
        objectMapper: ObjectMapper,
        applicationContext: ApplicationContext
    ): KotlinDataFetcherFactoryProvider = RedirectKotlinDataFetcherFactoryProvider(objectMapper, applicationContext)

    @Bean
    @ConditionalOnMissingBean
    fun connectionWrapperGraphQLTypeFactory(
        filters: List<TypeFilterDefinitionEntry>,
        dataFetcherFactoryProvider: KotlinDataFetcherFactoryProvider,
        additionalFilterBeans: Map<String, FilterEntryDefinition>
    ): ConnectionWrapperGraphQLTypeFactory {
        return ConnectionWrapperGraphQLTypeFactory(
            outputTypeCache,
            inputTypeCache,
            SubFilterGenerator(filters, filterDefinitions, nodeDefinitionCollection, additionalFilterBeans),
            tempCodeRegistry,
            dataFetcherFactoryProvider,
            neo4jMappingContext
        )
    }
}

private data class TopLevelQueryDefinition(val name: String, val graphQLWrapperType: GraphQLOutputType)
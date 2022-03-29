package io.github.graphglue.graphql

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
import io.github.graphglue.graphql.connection.ConnectionWrapperGraphQLTypeFactory
import io.github.graphglue.graphql.connection.filter.GraphglueGraphQLFilterConfiguration
import io.github.graphglue.graphql.connection.filter.TypeFilterDefinitionEntry
import io.github.graphglue.graphql.connection.filter.definition.FilterDefinitionCache
import io.github.graphglue.graphql.connection.filter.definition.FilterDefinitionCollection
import io.github.graphglue.graphql.connection.filter.definition.FilterEntryDefinition
import io.github.graphglue.graphql.connection.filter.definition.SubFilterGenerator
import io.github.graphglue.graphql.connection.order.OrderDirection
import io.github.graphglue.graphql.extensions.getSimpleName
import io.github.graphglue.graphql.extensions.springFindAnnotation
import io.github.graphglue.graphql.extensions.toTopLevelObjects
import io.github.graphglue.graphql.query.GraphglueQuery
import io.github.graphglue.graphql.query.TopLevelQueryProvider
import io.github.graphglue.graphql.redirect.RedirectKotlinDataFetcherFactoryProvider
import io.github.graphglue.graphql.redirect.rewireFieldType
import io.github.graphglue.model.DomainNode
import io.github.graphglue.model.Node
import io.github.graphglue.model.NodeSetProperty
import io.github.graphglue.model.PageInfo
import io.github.graphglue.neo4j.execution.NodeQueryParser
import io.github.graphglue.neo4j.execution.definition.NodeDefinition
import io.github.graphglue.neo4j.execution.definition.NodeDefinitionCache
import io.github.graphglue.neo4j.execution.definition.NodeDefinitionCollection
import io.github.graphglue.util.CacheMap
import graphql.schema.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.BeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.jvmErasure

/**
 * Configures beans used in combination with graphql-kotlin and graphql-java
 */
@Configuration
@Import(GraphglueGraphQLFilterConfiguration::class)
class GraphglueGraphQLConfiguration(private val neo4jMappingContext: Neo4jMappingContext) {

    private val logger = LoggerFactory.getLogger(io.github.graphglue.graphql.GraphglueGraphQLConfiguration::class.java)

    private val inputTypeCache = CacheMap<String, GraphQLInputType>()
    private val outputTypeCache = CacheMap<String, GraphQLOutputType>()
    private val filterDefinitions: FilterDefinitionCache = CacheMap()
    private val nodeDefinitions = CacheMap<KClass<out Node>, NodeDefinition>()
    private val nodeDefinitionCache = NodeDefinitionCache(nodeDefinitions, neo4jMappingContext)
    private val topLevelQueries = HashMap<NodeDefinition, io.github.graphglue.graphql.TopLevelQueryDefinition>()

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
            /**
             * Called after `willGenerateGraphQLType` and before `didGenerateGraphQLType`.
             * Enables you to change the wiring, e.g. apply directives to alter the target type.
             * Used to rewire relations
             */
            override fun onRewireGraphQLType(
                generatedType: GraphQLSchemaElement,
                coordinates: FieldCoordinates?,
                codeRegistry: GraphQLCodeRegistry.Builder
            ): GraphQLSchemaElement {
                codeRegistry.dataFetchers(tempCodeRegistry.build())
                tempCodeRegistry.clearDataFetchers()
                val rewiredType = super.onRewireGraphQLType(generatedType, coordinates, codeRegistry)
                return if (rewiredType is GraphQLFieldDefinition) {
                    rewireFieldType(rewiredType)
                } else {
                    return rewiredType
                }
            }

            /**
             * Called before using reflection to generate the graphql object type for the given KType.
             * This allows supporting objects that the caller does not want to use reflection on for special handling
             */
            override fun willGenerateGraphQLType(type: KType): GraphQLType? {
                if (type.isSubtypeOf(Node::class.createType())) {
                    @Suppress("UNCHECKED_CAST") val nodeClass = type.jvmErasure as KClass<out Node>
                    val nodeDefinition = nodeDefinitionCache.getOrCreate(nodeClass)
                    val domainNodeAnnotation = nodeClass.springFindAnnotation<DomainNode>()
                    val topLevelFunctionName = domainNodeAnnotation?.topLevelQueryName
                    if (topLevelFunctionName?.isNotBlank() == true) {
                        topLevelQueries[nodeDefinition] = io.github.graphglue.graphql.TopLevelQueryDefinition(
                            topLevelFunctionName,
                            factory.generateWrapperGraphQLType(nodeClass, nodeClass.getSimpleName())
                        )
                    }
                }
                return if (type.jvmErasure == NodeSetProperty.NodeSet::class) {
                    factory.generateWrapperGraphQLType(type)
                } else {
                    super.willGenerateGraphQLType(type)
                }
            }

            override fun willBuildSchema(builder: GraphQLSchema.Builder): GraphQLSchema.Builder {
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
    fun filterDefinitionCollection() = FilterDefinitionCollection(filterDefinitions)

    @Bean
    @ConditionalOnMissingBean
    fun queryParser(
        nodeDefinitionCollection: NodeDefinitionCollection,
        filterDefinitionCollection: FilterDefinitionCollection,
        objectMapper: ObjectMapper
    ) = NodeQueryParser(nodeDefinitionCollection, filterDefinitionCollection, objectMapper)

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
            supportedPackages = listOf("io.github.graphglue") + config.packages,
            topLevelNames = topLevelNames.orElse(TopLevelNames()),
            hooks = generatorHooks,
            dataFetcherFactoryProvider = dataFetcherFactoryProvider,
            introspectionEnabled = config.introspection.enabled
        )
    }

    @Bean
    @ConditionalOnMissingBean
    fun schemaAndNodeDefinitionCollection(
        queries: Optional<List<Query>>,
        mutations: Optional<List<Mutation>>,
        subscriptions: Optional<List<Subscription>>,
        schemaConfig: SchemaGeneratorConfig,
        beanFactory: BeanFactory
    ): io.github.graphglue.graphql.SchemaAndNodeDefinitionCollection {
        val generator = SchemaGenerator(schemaConfig)
        val nodeDefinition = nodeDefinitionCache.getOrCreate(Node::class)
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

        val nodeDefinitionCollection = NodeDefinitionCollection(nodeDefinitions, beanFactory)

        return io.github.graphglue.graphql.SchemaAndNodeDefinitionCollection(schema, nodeDefinitionCollection)
    }

    @Bean
    fun schema(schemaAndNodeDefinitionCollection: io.github.graphglue.graphql.SchemaAndNodeDefinitionCollection): GraphQLSchema =
        schemaAndNodeDefinitionCollection.schema

    @Bean
    fun nodeDefinitionCollection(schemaAndNodeDefinitionCollection: io.github.graphglue.graphql.SchemaAndNodeDefinitionCollection): NodeDefinitionCollection =
        schemaAndNodeDefinitionCollection.nodeDefinitionCollection

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
            SubFilterGenerator(filters, filterDefinitions, nodeDefinitionCache, additionalFilterBeans),
            tempCodeRegistry,
            dataFetcherFactoryProvider,
            neo4jMappingContext
        )
    }
}

private data class TopLevelQueryDefinition(val name: String, val graphQLWrapperType: GraphQLOutputType)

data class SchemaAndNodeDefinitionCollection(
    val schema: GraphQLSchema,
    val nodeDefinitionCollection: NodeDefinitionCollection
)
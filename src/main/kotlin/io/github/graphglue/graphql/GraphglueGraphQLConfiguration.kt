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
import graphql.schema.*
import io.github.graphglue.data.execution.NodeQueryParser
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.definition.NodeDefinitionCache
import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.connection.ConnectionWrapperGraphQLTypeFactory
import io.github.graphglue.connection.GraphglueConnectionConfiguration
import io.github.graphglue.connection.filter.TypeFilterDefinitionEntry
import io.github.graphglue.connection.filter.definition.*
import io.github.graphglue.connection.order.OrderDirection
import io.github.graphglue.graphql.datafetcher.RedirectKotlinDataFetcherFactoryProvider
import io.github.graphglue.graphql.datafetcher.rewireFieldType
import io.github.graphglue.graphql.extensions.getSimpleName
import io.github.graphglue.graphql.extensions.springFindAnnotation
import io.github.graphglue.graphql.extensions.toTopLevelObjects
import io.github.graphglue.graphql.query.GraphglueQuery
import io.github.graphglue.graphql.query.TopLevelQueryProvider
import io.github.graphglue.model.*
import io.github.graphglue.util.CacheMap
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.BeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
class GraphglueGraphQLConfiguration(private val neo4jMappingContext: Neo4jMappingContext) {

    /**
     * Logger used to print the GraphQL schema
     */
    private val logger = LoggerFactory.getLogger(GraphglueGraphQLConfiguration::class.java)

    /**
     * Cache for [GraphQLInputType]s
     */
    private val inputTypeCache = CacheMap<String, GraphQLInputType>()

    /**
     * Cache for [GraphQLOutputType]s
     */
    private val outputTypeCache = CacheMap<String, GraphQLOutputType>()

    /**
     * Cache for [FilterDefinition]s
     */
    private val filterDefinitions: FilterDefinitionCache = CacheMap()

    /**
     * Raw cache for [NodeDefinition]s
     */
    private val nodeDefinitions = CacheMap<KClass<out Node>, NodeDefinition>()

    /**
     * Cache for [NodeDefinition]s
     */
    private val nodeDefinitionCache = NodeDefinitionCache(nodeDefinitions, neo4jMappingContext)

    /**
     * Lookup for all queries based on [Node] types with the by the name for the query
     */
    private val topLevelQueries = HashMap<NodeDefinition, String>()

    /**
     * Code registry used as a temporary cache before its DataFetchers are added to the
     * real code registry
     * must only be used to
     */
    private val tempCodeRegistry: GraphQLCodeRegistry.Builder = GraphQLCodeRegistry.newCodeRegistry()

    /**
     * Provides the [SchemaGeneratorHooks] for the [SchemaGeneratorConfig]
     *
     * @param factory used to build connection wrapper GraphQL types for properties
     * @param applicationContext used for bean lookup
     * @param objectMapper used for cursor serialization and deserialization
     * @param dataFetcherFactoryProvider used to generate function data fetchers for [Node] based queries
     * @return the generated [GraphglueSchemaGeneratorHooks]
     */
    @Bean
    @ConditionalOnMissingBean
    fun schemaGeneratorHooks(
        factory: ConnectionWrapperGraphQLTypeFactory,
        applicationContext: ApplicationContext,
        objectMapper: ObjectMapper,
        dataFetcherFactoryProvider: KotlinDataFetcherFactoryProvider
    ): SchemaGeneratorHooks {
        return GraphglueSchemaGeneratorHooks(factory, applicationContext, objectMapper, dataFetcherFactoryProvider)
    }

    /**
     * Generates the [FilterDefinitionCollection] based on the internal [filterDefinitions]
     *
     * @return the generated [FilterDefinitionCollection]
     */
    @Bean
    @ConditionalOnMissingBean
    fun filterDefinitionCollection() = FilterDefinitionCollection(filterDefinitions)

    /**
     * Parser for incoming GraphQL queries
     * Allows transforming (a part of) a GraphQL query into a single Cypher query
     *
     * @param nodeDefinitionCollection collection of all [NodeDefinition]s
     * @param filterDefinitionCollection collection of all [FilterDefinition]s
     * @param objectMapper necessary for cursor serialization and deserialization
     * @return the generated [NodeQueryParser]
     */
    @Bean
    @ConditionalOnMissingBean
    fun queryParser(
        nodeDefinitionCollection: NodeDefinitionCollection,
        filterDefinitionCollection: FilterDefinitionCollection,
        objectMapper: ObjectMapper
    ) = NodeQueryParser(nodeDefinitionCollection, filterDefinitionCollection, objectMapper)

    /**
     * Config for the schema generator, automatically adds `io.github.graphglue` to the supported packages
     *
     * @param config used to generate the [SchemaGeneratorConfig]
     * @param topLevelNames see [SchemaGeneratorConfig.topLevelNames]
     * @param hooks see [SchemaGeneratorConfig.hooks]
     * @param dataFetcherFactoryProvider see [SchemaGeneratorConfig.dataFetcherFactoryProvider]
     * @return the schema config
     */
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

    /**
     * Generates the [GraphQLSchema] and the [NodeDefinitionCollection]
     * Automatically adds the generated [Node] type based connection queries
     *
     * @param queries queries used in GraphQL schema
     * @param mutations mutations used in GraphQL schema
     * @param subscriptions subscriptions used in GraphQL schema
     * @param schemaConfig see [SchemaGenerator.config]
     * @param beanFactory necessary for [NodeDefinitionCollection]
     * @return both the generated [GraphQLSchema] and [NodeDefinitionCollection]
     */
    @Bean
    @ConditionalOnMissingBean
    fun schemaAndNodeDefinitionCollection(
        queries: Optional<List<Query>>,
        mutations: Optional<List<Mutation>>,
        subscriptions: Optional<List<Subscription>>,
        schemaConfig: SchemaGeneratorConfig,
        beanFactory: BeanFactory
    ): SchemaAndNodeDefinitionCollection {
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
        return SchemaAndNodeDefinitionCollection(schema, nodeDefinitionCollection)
    }

    /**
     * [GraphQLSchema] based on [SchemaAndNodeDefinitionCollection]
     *
     * @param schemaAndNodeDefinitionCollection provides the [GraphQLSchema]
     * @return the [GraphQLSchema]
     */
    @Bean
    fun schema(schemaAndNodeDefinitionCollection: SchemaAndNodeDefinitionCollection): GraphQLSchema =
        schemaAndNodeDefinitionCollection.schema

    /**
     * [NodeDefinitionCollection] based on [SchemaAndNodeDefinitionCollection]
     *
     * @param schemaAndNodeDefinitionCollection provides the [NodeDefinitionCollection]
     * @return the [NodeDefinitionCollection]
     */
    @Bean
    fun nodeDefinitionCollection(schemaAndNodeDefinitionCollection: SchemaAndNodeDefinitionCollection): NodeDefinitionCollection =
        schemaAndNodeDefinitionCollection.nodeDefinitionCollection

    /**
     * Specific [KotlinDataFetcherFactoryProvider] which handles [BaseProperty] backed properties correctly.
     * If you want to replace this, please extend [RedirectKotlinDataFetcherFactoryProvider] if possible
     *
     * @param objectMapper necessary for jackson
     * @param applicationContext necessary for bean injection
     * @return the generated [RedirectKotlinDataFetcherFactoryProvider]
     */
    @Bean
    @ConditionalOnMissingBean
    fun dataFetcherFactoryProvider(
        objectMapper: ObjectMapper, applicationContext: ApplicationContext
    ): KotlinDataFetcherFactoryProvider = RedirectKotlinDataFetcherFactoryProvider(objectMapper, applicationContext)

    /**
     * Generates a factory for connection GraphQL wrapper types
     *
     * @param filters type based definitions for filters, necessary for [SubFilterGenerator]
     * @param dataFetcherFactoryProvider provides property and function data fetchers
     * @param additionalFilterBeans filters defined by bean name instead of type, by bean name
     * @return the generated [ConnectionWrapperGraphQLTypeFactory]
     */
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

    /**
     * [SchemaGeneratorHooks] which handles rewiring of [BaseProperty] backed files,
     * collects all [Node] types and generates [NodeDefinition]s for it, and collects queries
     * for [Node] types
     *
     * @param factory used to build connection wrapper GraphQL types for properties
     * @param applicationContext used for bean lookup
     * @param objectMapper used for cursor serialization and deserialization
     * @param dataFetcherFactoryProvider used to generate function data fetchers for [Node] based queries
     */
    inner class GraphglueSchemaGeneratorHooks(
        private val factory: ConnectionWrapperGraphQLTypeFactory,
        private val applicationContext: ApplicationContext,
        private val objectMapper: ObjectMapper,
        private val dataFetcherFactoryProvider: KotlinDataFetcherFactoryProvider
    ) : SchemaGeneratorHooks {

        /**
         * Called after `willGenerateGraphQLType` and before `didGenerateGraphQLType`.
         * Enables you to change the wiring, e.g. apply directives to alter the target type.
         * Used to rewire [BaseProperty] based properties
         *
         * @param generatedType the GraphQL type which was generated by graphql-kotlin
         * @param coordinates coordinates or the field
         * @param codeRegistry used to register the data fetcher
         * @return the transformed [GraphQLSchemaElement]
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
         * This allows supporting objects that the caller does not want to use reflection on for special handling.
         * Used to collect [NodeDefinition]s for [Node] types
         * Also collects the [topLevelQueries]
         *
         * @param type the type for which to generate a [GraphQLType]
         * @return the generated [GraphQLType]
         */
        override fun willGenerateGraphQLType(type: KType): GraphQLType? {
            if (type.isSubtypeOf(Node::class.createType())) {
                @Suppress("UNCHECKED_CAST") val nodeClass = type.jvmErasure as KClass<out Node>
                val nodeDefinition = nodeDefinitionCache.getOrCreate(nodeClass)
                val domainNodeAnnotation = nodeClass.springFindAnnotation<DomainNode>()
                val topLevelFunctionName = domainNodeAnnotation?.topLevelQueryName
                if (topLevelFunctionName?.isNotBlank() == true) {
                    topLevelQueries[nodeDefinition] = topLevelFunctionName
                }
            }
            return if (type.jvmErasure == NodeSetProperty.NodeSet::class) {
                factory.generateWrapperGraphQLType(type)
            } else {
                super.willGenerateGraphQLType(type)
            }
        }

        /**
         * Called before the schema is finally build.
         * Adds the missing connection like queries for [Node] types declared using the [DomainNode] annotation.
         * Calls `willBuildSchema` on `super`
         *
         * @param builder the builder for the incomplete [GraphQLSchema]
         * @return a builder for the new schema with the additional queries
         */
        override fun willBuildSchema(builder: GraphQLSchema.Builder): GraphQLSchema.Builder {
            return super.willBuildSchema(completeSchema(builder))
        }

        /**
         * Adds the missing connection like queries for [Node] types declared using the [DomainNode] annotation
         *
         * @param builder the builder for the incomplete [GraphQLSchema]
         * @return a builder for the new schema with the additional queries
         */
        private fun completeSchema(builder: GraphQLSchema.Builder): GraphQLSchema.Builder {
            val schema = builder.build()
            val codeRegistry = schema.codeRegistry
            val newBuilder = GraphQLSchema.newSchema(builder.build())
            updateQueryType(schema, newBuilder)
            newBuilder.codeRegistry(codeRegistry.transform {
                it.dataFetchers(tempCodeRegistry.build())
            })
            return newBuilder
        }

        /**
         * Adds the missing connection like queries for [Node] types declared using the [DomainNode] annotation
         *
         * @param schema the existing schema
         * @param newBuilder builder for the new schema
         */
        private fun updateQueryType(schema: GraphQLSchema, newBuilder: GraphQLSchema.Builder) {
            val queryType = schema.queryType!!
            val newQueryType = queryType.transform {
                val function = TopLevelQueryProvider::class.memberFunctions.first { it.name == "getFromGraphQL" }
                for ((nodeDefinition, queryName) in topLevelQueries) {
                    val nodeClass = nodeDefinition.nodeType
                    val wrapperType = factory.generateWrapperGraphQLType(nodeClass, nodeClass.getSimpleName())
                    wrapperType as GraphQLObjectType
                    val field = wrapperType.fields.first().transform { fieldBuilder ->
                        fieldBuilder.name(queryName)
                            .description("Query for nodes of type ${nodeClass.getSimpleName()}")
                    }
                    it.field(field)
                    val coordinates = FieldCoordinates.coordinates(queryType.name, queryName)
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
 * Named tuple consisting of a [GraphQLSchema] and a [NodeDefinitionCollection]
 * Helper type for a bean which has to construct both at the same time
 *
 * @param schema the [GraphQLSchema]
 * @param nodeDefinitionCollection the [NodeDefinitionCollection]
 */
data class SchemaAndNodeDefinitionCollection(
    val schema: GraphQLSchema, val nodeDefinitionCollection: NodeDefinitionCollection
)
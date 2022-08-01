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
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLType
import io.github.graphglue.connection.filter.NodeFilterGenerator
import io.github.graphglue.connection.filter.TypeFilterDefinitionEntry
import io.github.graphglue.connection.filter.definition.FilterDefinitionCollection
import io.github.graphglue.connection.filter.definition.FilterEntryDefinition
import io.github.graphglue.connection.filter.definition.SubFilterGenerator
import io.github.graphglue.connection.order.OrderDirection
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.graphql.extensions.toTopLevelObjects
import io.github.graphglue.graphql.query.GraphglueQuery
import io.github.graphglue.graphql.schema.DefaultSchemaTransformer
import io.github.graphglue.model.Node
import io.github.graphglue.model.NodeRelationship
import io.github.graphglue.connection.model.PageInfo
import io.github.graphglue.util.CacheMap
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.createType
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.starProjectedType

/**
 * Configures beans used in combination with graphql-kotlin and graphql-java
 */
@Configuration
class GraphglueGraphQLConfiguration {

    /**
     * Logger used to print the GraphQL schema
     */
    private val logger = LoggerFactory.getLogger(GraphglueGraphQLConfiguration::class.java)

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
        dataFetcherFactoryProvider: KotlinDataFetcherFactoryProvider,
        additionalTypes: Set<GraphQLType>
    ): SchemaGeneratorConfig {
        val generatorHooks = hooks.orElse(NoopSchemaGeneratorHooks)
        return SchemaGeneratorConfig(
            supportedPackages = listOf("io.github.graphglue") + config.packages,
            topLevelNames = topLevelNames.orElse(TopLevelNames()),
            hooks = GraphglueSchemaGeneratorHooks(generatorHooks),
            dataFetcherFactoryProvider = dataFetcherFactoryProvider,
            introspectionEnabled = config.introspection.enabled,
            additionalTypes = additionalTypes
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
     * @param filters type based definitions for filters, necessary for [SubFilterGenerator]
     * @param dataFetcherFactoryProvider provides property and function data fetchers
     * @param additionalFilterBeans filters defined by bean name instead of type, by bean name
     * @param nodeDefinitionCollection used to get [NodeDefinition]s
     * @param nodeFilterGenerators used to create
     * @param neo4jMappingContext necessary for [DefaultSchemaTransformer]
     * @return both the generated [GraphQLSchema] and [NodeDefinitionCollection]
     */
    @Bean
    @ConditionalOnMissingBean
    fun graphglueSchema(
        queries: Optional<List<Query>>,
        mutations: Optional<List<Mutation>>,
        subscriptions: Optional<List<Subscription>>,
        schemaConfig: SchemaGeneratorConfig,
        dataFetcherFactoryProvider: KotlinDataFetcherFactoryProvider,
        filters: List<TypeFilterDefinitionEntry>,
        additionalFilterBeans: Map<String, FilterEntryDefinition>,
        nodeDefinitionCollection: NodeDefinitionCollection,
        nodeFilterGenerators: List<NodeFilterGenerator>,
        neo4jMappingContext: Neo4jMappingContext
    ): GraphglueSchema {
        val generator = SchemaGenerator(schemaConfig)
        val nodeDefinition = nodeDefinitionCollection.getNodeDefinition<Node>()
        val additionalTypes =
            mutableSetOf(PageInfo::class.createType()) + nodeDefinitionCollection.map { it.nodeType.starProjectedType }
        val schema = generator.use {
            it.generateSchema(
                queries.orElse(emptyList()).toTopLevelObjects() + TopLevelObject(GraphglueQuery(nodeDefinition)),
                mutations.orElse(emptyList()).toTopLevelObjects(),
                subscriptions.orElse(emptyList()).toTopLevelObjects(),
                additionalTypes = additionalTypes,
                additionalInputTypes = setOf(OrderDirection::class.createType()),
            )
        }
        val schemaTransformer = DefaultSchemaTransformer(
            schema, neo4jMappingContext, nodeDefinitionCollection, dataFetcherFactoryProvider, SubFilterGenerator(
                filters, CacheMap(), nodeDefinitionCollection, additionalFilterBeans, nodeFilterGenerators
            )
        )
        logger.info("\n${schemaTransformer.schema.print()}")
        return GraphglueSchema(schemaTransformer.schema, schemaTransformer.filterDefinitionCollection)
    }

    /**
     * [GraphQLSchema] based on [GraphglueSchema]
     *
     * @param schema provides the [GraphQLSchema]
     * @return the [GraphQLSchema]
     */
    @Bean
    fun schema(schema: GraphglueSchema): GraphQLSchema = schema.schema

    /**
     * [FilterDefinitionCollection] based on [GraphglueSchema]
     *
     * @param schema provides the [FilterDefinitionCollection]
     * @return the [FilterDefinitionCollection]
     */
    @Bean
    fun filterDefinitionCollection(schema: GraphglueSchema): FilterDefinitionCollection =
        schema.filterDefinitionCollection

    /**
     * [SchemaGeneratorHooks] which handles rewiring of [BasePropertyDelegate] backed files,
     * collects all [Node] types and generates [NodeDefinition]s for it, and collects queries
     * for [Node] types
     *
     * @param delegate delegate used as fallback for all functions, used to provide better extensibility
     */
    inner class GraphglueSchemaGeneratorHooks(private val delegate: SchemaGeneratorHooks) :
        SchemaGeneratorHooks by delegate {

        /**
         * Called to check if a property should be included in the schema
         * Ignores all properties annotated with [NodeRelationship], as those are handled manually
         *
         * @param kClass the class which contains the property
         * @param property the property to validate
         * @return `true` if the property should be included in the schema
         */
        override fun isValidProperty(kClass: KClass<*>, property: KProperty<*>): Boolean {
            return delegate.isValidProperty(kClass, property) && !property.hasAnnotation<NodeRelationship>()
        }

        /**
         * Checks if an additional type is valid
         * Used when adding subtypes of interfaces
         * Ignores [Node] types, as those are handled manually
         *
         * @param kClass the  class to check
         * @param inputType shall an input type be generated
         * @return `true` if the class should be added to the additional types to generate
         */
        override fun isValidAdditionalType(kClass: KClass<*>, inputType: Boolean): Boolean {
            return if (kClass.isSubclassOf(Node::class) && delegate.isValidAdditionalType(kClass, inputType)) {
                false
            } else {
                delegate.isValidAdditionalType(kClass, inputType)
            }
        }

        /**
         * Checks if a superclass is valid
         * Used when adding interfaces to an object type
         * Ignores [Node] types, as those are handled manually
         *
         * @param kClass the  class to check
         * @return `true` if the class should be added as an interface
         */
        override fun isValidSuperclass(kClass: KClass<*>): Boolean {
            return if (kClass.isSubclassOf(Node::class) && delegate.isValidSuperclass(kClass)) {
                false
            } else {
                delegate.isValidSuperclass(kClass)
            }
        }
    }
}

/**
 * Named tuple consisting of a [GraphQLSchema] and a [NodeDefinitionCollection]
 * Helper type for a bean which has to construct both at the same time
 *
 * @param schema the [GraphQLSchema]
 * @param filterDefinitionCollection the [FilterDefinitionCollection]
 */
data class GraphglueSchema(
    val schema: GraphQLSchema, val filterDefinitionCollection: FilterDefinitionCollection
)
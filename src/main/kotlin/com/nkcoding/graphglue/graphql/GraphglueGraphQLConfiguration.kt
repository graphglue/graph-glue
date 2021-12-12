package com.nkcoding.graphglue.graphql

import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelNames
import com.expediagroup.graphql.generator.execution.KotlinDataFetcherFactoryProvider
import com.expediagroup.graphql.generator.hooks.NoopSchemaGeneratorHooks
import com.expediagroup.graphql.generator.hooks.SchemaGeneratorHooks
import com.expediagroup.graphql.server.spring.GraphQLConfigurationProperties
import com.nkcoding.graphglue.graphql.connection.filter.GraphglueGraphQLFilterConfiguration
import com.nkcoding.graphglue.graphql.connection.filter.TypeFilterDefinitionEntry
import com.nkcoding.graphglue.graphql.connection.filter.definition.FilterDefinition
import com.nkcoding.graphglue.graphql.connection.filter.definition.FilterDefinitionCache
import com.nkcoding.graphglue.graphql.connection.filter.definition.FilterDefinitionCollection
import com.nkcoding.graphglue.graphql.connection.filter.definition.SubFilterGenerator
import com.nkcoding.graphglue.graphql.connection.generateWrapperGraphQLType
import com.nkcoding.graphglue.graphql.redirect.rewireFieldType
import com.nkcoding.graphglue.model.Node
import com.nkcoding.graphglue.model.NodeConnection
import graphql.schema.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

/**
 * Configures beans used in combination with graphql-kotlin and graphql-java
 */
@Configuration
@Import(GraphglueGraphQLFilterConfiguration::class)
class GraphglueGraphQLConfiguration {

    private val inputTypeCache = ConcurrentHashMap<String, GraphQLInputObjectType>()
    private val outputTypeCache = ConcurrentHashMap<String, GraphQLObjectType>()
    private val filterDefinitions: FilterDefinitionCache = ConcurrentHashMap<KClass<out Node>, FilterDefinition<out Node>>()

    /**
     * Provides the [SchemaGeneratorHooks] for the [SchemaGeneratorConfig]
     */
    @Bean
    @ConditionalOnMissingBean
    fun schemaGeneratorHooks(filters: List<TypeFilterDefinitionEntry>): SchemaGeneratorHooks {
        return object : SchemaGeneratorHooks {
            override fun onRewireGraphQLType(
                generatedType: GraphQLSchemaElement,
                coordinates: FieldCoordinates?,
                codeRegistry: GraphQLCodeRegistry.Builder
            ): GraphQLSchemaElement {
                val rewiredType = super.onRewireGraphQLType(generatedType, coordinates, codeRegistry)
                return if (rewiredType is GraphQLFieldDefinition) {
                    rewireFieldType(rewiredType, coordinates, codeRegistry)
                } else {
                    return rewiredType
                }
            }

            override fun willGenerateGraphQLType(type: KType): GraphQLType? {
                return if (type.jvmErasure == NodeConnection::class) {
                    generateWrapperGraphQLType(type, outputTypeCache, inputTypeCache,
                        SubFilterGenerator(filters, filterDefinitions))
                } else {
                    super.willGenerateGraphQLType(type)
                }
            }
        }
    }

    /**
     * Gets a list of all filter definitions
     */
    @Bean
    fun filterDefinitions() = FilterDefinitionCollection(filterDefinitions)

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
}
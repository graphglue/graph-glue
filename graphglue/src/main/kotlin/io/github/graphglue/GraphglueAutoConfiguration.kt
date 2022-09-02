package io.github.graphglue

import com.expediagroup.graphql.server.spring.GraphQLAutoConfiguration
import io.github.graphglue.graphql.GraphglueGraphQLConfiguration

import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Manages Spring boot autoconfiguration for all Graphglue related nodes
 * Imports the GraphglueGraphQLConfiguration and GraphglueNeo4jConfiguration
 */
@Configuration
@Import(
    GraphglueGraphQLConfiguration::class, GraphiQLRouteConfiguration::class
)
@EnableConfigurationProperties(GraphglueConfigurationProperties::class)
@AutoConfigureBefore(
    value = [GraphQLAutoConfiguration::class]
)
@AutoConfigureAfter(
    value = [GraphglueCoreAutoConfiguration::class]
)
class GraphglueAutoConfiguration
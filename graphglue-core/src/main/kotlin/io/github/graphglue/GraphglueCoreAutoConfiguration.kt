package io.github.graphglue

import io.github.graphglue.authorization.GraphglueAuthorizationConfiguration
import io.github.graphglue.connection.GraphglueConnectionConfiguration
import io.github.graphglue.data.GraphglueDataConfiguration
import io.github.graphglue.definition.GraphglueDefinitionConfiguration
import io.github.graphglue.model.GraphglueModelConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.data.neo4j.autoconfigure.DataNeo4jReactiveAutoConfiguration
import org.springframework.boot.neo4j.autoconfigure.Neo4jAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import

/**
 * Manages Spring boot autoconfiguration for all Graphglue related nodes
 * Imports the GraphglueGraphQLConfiguration and GraphglueNeo4jConfiguration
 */
@AutoConfiguration
@Import(
    GraphglueModelConfiguration::class,
    GraphglueDataConfiguration::class,
    GraphglueAuthorizationConfiguration::class,
    GraphglueConnectionConfiguration::class,
    GraphglueDefinitionConfiguration::class
)
@EnableConfigurationProperties(GraphglueCoreConfigurationProperties::class)
@AutoConfigureAfter(
    value = [Neo4jAutoConfiguration::class, DataNeo4jReactiveAutoConfiguration::class]
)
class GraphglueCoreAutoConfiguration
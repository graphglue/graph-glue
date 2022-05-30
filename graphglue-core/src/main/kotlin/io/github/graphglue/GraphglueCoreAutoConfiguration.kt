package io.github.graphglue

import io.github.graphglue.authorization.GraphglueAuthorizationConfiguration
import io.github.graphglue.connection.GraphglueConnectionConfiguration
import io.github.graphglue.data.GraphglueDataConfiguration
import io.github.graphglue.definition.GraphglueDefinitionConfiguration
import io.github.graphglue.model.GraphglueModelConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jReactiveDataAutoConfiguration
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Manages Spring boot autoconfiguration for all Graphglue related nodes
 * Imports the GraphglueGraphQLConfiguration and GraphglueNeo4jConfiguration
 */
@Configuration
@Import(
    GraphglueModelConfiguration::class,
    GraphglueDataConfiguration::class,
    GraphglueAuthorizationConfiguration::class,
    GraphglueConnectionConfiguration::class,
    GraphglueDefinitionConfiguration::class
)
@AutoConfigureAfter(
    value = [Neo4jAutoConfiguration::class, Neo4jReactiveDataAutoConfiguration::class]
)
class GraphglueCoreAutoConfiguration
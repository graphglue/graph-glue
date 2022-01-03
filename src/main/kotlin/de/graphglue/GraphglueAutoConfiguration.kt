package de.graphglue

import com.expediagroup.graphql.server.spring.GraphQLAutoConfiguration
import de.graphglue.graphql.GraphglueGraphQLConfiguration
import de.graphglue.neo4j.GraphglueNeo4jConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jReactiveDataAutoConfiguration
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Manages Spring boot autoconfiguration for all Graphglue related nodes
 */
@Configuration
@Import(GraphglueGraphQLConfiguration::class, GraphglueNeo4jConfiguration::class)
@AutoConfigureBefore(
    value = [GraphQLAutoConfiguration::class]
)
@AutoConfigureAfter(
    value = [Neo4jAutoConfiguration::class, Neo4jReactiveDataAutoConfiguration::class]
)
class GraphglueAutoConfiguration
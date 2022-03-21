package de.graphglue

import com.expediagroup.graphql.server.spring.GraphQLAutoConfiguration
import de.graphglue.graphql.GraphglueGraphQLConfiguration
import de.graphglue.model.NODE_ID_GENERATOR_BEAN
import de.graphglue.neo4j.GraphglueNeo4jConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jReactiveDataAutoConfiguration
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.neo4j.core.support.UUIDStringGenerator

/**
 * Manages Spring boot autoconfiguration for all Graphglue related nodes
 * Imports the GraphglueGraphQLConfiguration and GraphglueNeo4jConfiguration
 */
@Configuration
@Import(GraphglueGraphQLConfiguration::class, GraphglueNeo4jConfiguration::class)
@AutoConfigureBefore(
    value = [GraphQLAutoConfiguration::class]
)
@AutoConfigureAfter(
    value = [Neo4jAutoConfiguration::class, Neo4jReactiveDataAutoConfiguration::class]
)
class GraphglueAutoConfiguration {

    @Bean(NODE_ID_GENERATOR_BEAN)
    @ConditionalOnMissingBean
    fun nodeIdGenerator() = UUIDStringGenerator()
}
package io.github.graphglue

import com.expediagroup.graphql.server.spring.GraphQLAutoConfiguration
import io.github.graphglue.model.NODE_ID_GENERATOR_BEAN
import io.github.graphglue.db.GraphglueDbConfiguration
import io.github.graphglue.graphql.GraphglueGraphQLConfiguration
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
@Import(GraphglueGraphQLConfiguration::class, GraphglueDbConfiguration::class)
@AutoConfigureBefore(
    value = [GraphQLAutoConfiguration::class]
)
@AutoConfigureAfter(
    value = [Neo4jAutoConfiguration::class, Neo4jReactiveDataAutoConfiguration::class]
)
class GraphglueAutoConfiguration {

    /**
     * Default node id generator
     * @return the generator for node ids
     */
    @Bean(NODE_ID_GENERATOR_BEAN)
    @ConditionalOnMissingBean
    fun nodeIdGenerator() = UUIDStringGenerator()
}
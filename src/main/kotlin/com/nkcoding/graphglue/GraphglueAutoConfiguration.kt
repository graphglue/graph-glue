package com.nkcoding.graphglue

import com.nkcoding.graphglue.graphql.GraphglueGraphQLConfiguration
import com.nkcoding.graphglue.neo4j.GraphglueNeo4jConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Manages Spring boot autoconfiguration for all Graphglue related nodes
 */
@Configuration
@Import(GraphglueGraphQLConfiguration::class, GraphglueNeo4jConfiguration::class)
class GraphglueAutoConfiguration
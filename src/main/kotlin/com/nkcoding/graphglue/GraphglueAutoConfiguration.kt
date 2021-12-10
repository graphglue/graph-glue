package com.nkcoding.graphglue

import com.nkcoding.graphglue.graphql.GraphglueGraphQLConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Manages Spring boot autoconfiguration for all Graphglue related nodes
 */
@Configuration
@Import(GraphglueGraphQLConfiguration::class)
class GraphglueAutoConfiguration
package io.github.graphglue

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for Graphglue core
 *
 * @param maxQueryCost The maximum allowed query complexity
 */
@ConfigurationProperties("graphglue.core")
class GraphglueCoreConfigurationProperties(
    val maxQueryCost: Int = 10,
    val useNeo4jPlugin: Boolean = false
)
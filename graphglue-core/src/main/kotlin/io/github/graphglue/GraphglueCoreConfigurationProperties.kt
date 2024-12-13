package io.github.graphglue

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for Graphglue core
 *
 * @param maxQueryCost The maximum allowed query complexity
 * @param useNeo4jPlugin If true, authorization checks use the io.github.graphglue.authorizationPath custom procedure to improve efficiency. This procedure must be provided via a database plugin
 * @param enableDuplicateSafePrevention If true, saving a node twice will throw an exception. This is the recommended setting, in particular when using optimistic locking
 */
@ConfigurationProperties("graphglue.core")
class GraphglueCoreConfigurationProperties(
    val maxQueryCost: Int = 10, val useNeo4jPlugin: Boolean = false, val enableDuplicateSafePrevention: Boolean = true
)
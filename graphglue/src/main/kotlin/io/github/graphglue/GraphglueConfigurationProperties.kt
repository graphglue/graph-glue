package io.github.graphglue

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

/**
 * Configuration properties for graphglue
 *
 * @param graphiql GraphiQL related configuration properties
 */
@ConstructorBinding
@ConfigurationProperties("graphglue")
data class GraphglueConfigurationProperties(
    val graphiql: GraphiQLConfigurationProperties = GraphiQLConfigurationProperties()
) {

    /**
     * GraphiQL configuration properties.
     *
     * @param enabled if `true`, an GraphiQL instance will be served under [endpoint]
     * @param endpoint the GraphiQL endpoint, defaults to `"graphiql`"
     */
    data class GraphiQLConfigurationProperties(
        val enabled: Boolean = true,
        val endpoint: String = "graphiql"
    )

}
package io.github.graphglue.authorization

import io.github.graphglue.definition.NodeDefinitionCollection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.neo4j.core.ReactiveNeo4jClient

/**
 * Autoconfiguration associated with authorization part of library
 */
@Configuration
class GraphglueAuthorizationConfiguration {

    /**
     * Bean to provide [AuthorizationChecker] which support checking if a certain permission is given
     *
     * @param collection collection in which the node is defined
     * @param client client used to execute queries
     * @return the created [AuthorizationChecker] which supports checking if a certain permission is given
     */
    @Bean
    fun authorizationChecker(
        collection: NodeDefinitionCollection,
        client: ReactiveNeo4jClient
    ): AuthorizationChecker = AuthorizationChecker(collection, client)
}
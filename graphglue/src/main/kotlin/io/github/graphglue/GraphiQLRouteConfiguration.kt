/*
 * Copyright 2021 Expedia, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * This is a modified version
 */

package io.github.graphglue

import com.expediagroup.graphql.server.spring.GraphQLConfigurationProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.reactive.function.server.html

/**
 * Configuration for exposing the GraphiQL on a specific HTTP path
 *
 * @param config GraphQL Kotlin config to obtain endpoints
 * @param graphglueConfig used to configure the endpoint of the GraphiQL instance
 * @param graphiQLHtml the HTML template to serve
 * @param contextPath base path to build endpoint URLs
 */
@ConditionalOnProperty(value = ["graphglue.graphiql.enabled"], havingValue = "true", matchIfMissing = true)
@Configuration
class GraphiQLRouteConfiguration(
    private val config: GraphQLConfigurationProperties,
    private val graphglueConfig: GraphglueConfigurationProperties,
    @Value("classpath:/graphiql.html")
    private val graphiQLHtml: Resource,
    @Value("\${spring.webflux.base-path:#{null}}")
    private val contextPath: String?
) {

    private val body = graphiQLHtml.inputStream.bufferedReader().use { reader ->
        val graphQLEndpoint = if (contextPath.isNullOrBlank()) config.endpoint else "$contextPath/${config.endpoint}"
        val subscriptionsEndpoint =
            if (contextPath.isNullOrBlank()) config.subscriptions.endpoint else "$contextPath/${config.subscriptions.endpoint}"

        reader.readText().replace("\${graphQLEndpoint}", graphQLEndpoint)
            .replace("\${subscriptionsEndpoint}", subscriptionsEndpoint)
    }

    /**
     * Route for the GraphIQL instance
     * @return the generated router serving the GraphiQL instance
     */
    @Bean
    fun graphiQLRoute() = coRouter {
        GET(graphglueConfig.graphiql.endpoint) {
            ok().html().bodyValueAndAwait(body)
        }
    }
}
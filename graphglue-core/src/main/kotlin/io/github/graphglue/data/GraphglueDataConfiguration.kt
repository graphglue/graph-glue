package io.github.graphglue.data

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.graphglue.connection.filter.definition.FilterDefinition
import io.github.graphglue.connection.filter.definition.FilterDefinitionCollection
import io.github.graphglue.data.execution.NodeQueryParser
import io.github.graphglue.data.repositories.GraphglueNeo4jOperations
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.model.Node
import org.neo4j.driver.types.MapAccessor
import org.springframework.beans.factory.BeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.neo4j.core.Neo4jOperations
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.ReactiveNeo4jTemplate
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import org.springframework.data.neo4j.core.mapping.Neo4jPersistentEntity
import org.springframework.data.neo4j.core.mapping.callback.AfterConvertCallback
import java.util.*

/**
 * Name for the bean which provides an instance of  [GraphglueNeo4jOperations]
 */
const val GRAPHGLUE_NEO4J_OPERATIONS_BEAN_NAME = "graphglueNeo4jOperations"

/**
 * Autoconfiguration associated with Neo4j part of library
 */
@Configuration
class GraphglueDataConfiguration {

    /**
     * [AfterConvertCallback] bean which injects the [LazyLoadingContext] into all loaded [Node]s
     *
     * @param beanFactory used to obtain the [LazyLoadingContext]
     */
    @Bean
    fun lazyLoadingContextInjector(beanFactory: BeanFactory): AfterConvertCallback<Node> =
        object : AfterConvertCallback<Node> {

            /**
             * The lazy loaded (necessary to avoid circular dependency) [LazyLoadingContext]
             */
            private val lazyLoadingContext by lazy { beanFactory.getBean(LazyLoadingContext::class.java) }

            override fun onAfterConvert(
                instance: Node, entity: Neo4jPersistentEntity<Node>, source: MapAccessor
            ): Node {
                instance.lazyLoadingContext = lazyLoadingContext
                return instance
            }
        }

    /**
     * Creates a new [LazyLoadingContext]
     *
     * @param neo4jClient client used to perform Cypher queries
     * @param neo4jMappingContext context used to get mapping functions
     * @param nodeQueryParser used to generate the Cypher query
     * @return the generated [LazyLoadingContext]
     */
    @Bean
    fun lazyLoadingContext(
        neo4jClient: ReactiveNeo4jClient, neo4jMappingContext: Neo4jMappingContext, nodeQueryParser: NodeQueryParser
    ): LazyLoadingContext {
        return LazyLoadingContext(neo4jClient, neo4jMappingContext, nodeQueryParser)
    }

    /**
     * Bean to provide [Neo4jOperations] which support save over lazy loaded relations
     *
     * @param neo4jTemplate template which provides base operation functionality
     * @param neo4jClient client used to execute queries
     * @param beanFactory used to get the [NodeDefinitionCollection]
     * @return the created [GraphglueNeo4jOperations] which supports save of lazy loaded relations
     */
    @Bean(GRAPHGLUE_NEO4J_OPERATIONS_BEAN_NAME)
    fun graphGlueNeo4jOperations(
        neo4jTemplate: ReactiveNeo4jTemplate, neo4jClient: ReactiveNeo4jClient, beanFactory: BeanFactory
    ) = GraphglueNeo4jOperations(neo4jTemplate, neo4jClient, beanFactory)

    /**
     * Parser for incoming GraphQL queries
     * Allows transforming (a part of) a GraphQL query into a single Cypher query
     *
     * @param nodeDefinitionCollection collection of all [NodeDefinition]s
     * @param filterDefinitionCollection collection of all [FilterDefinition]s, if existing
     * @param objectMapper necessary for cursor serialization and deserialization
     * @return the generated [NodeQueryParser]
     */
    @Bean
    @ConditionalOnMissingBean
    fun queryParser(
        nodeDefinitionCollection: NodeDefinitionCollection,
        filterDefinitionCollection: Optional<FilterDefinitionCollection>,
        objectMapper: ObjectMapper
    ) = NodeQueryParser(nodeDefinitionCollection, filterDefinitionCollection.orElse(null), objectMapper)
}
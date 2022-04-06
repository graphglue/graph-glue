package io.github.graphglue.data

import io.github.graphglue.data.execution.NodeQueryParser
import io.github.graphglue.data.repositories.AuthorizationChecker
import io.github.graphglue.data.repositories.AuthorizationCheckerImpl
import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.data.repositories.GraphglueNeo4jOperations
import org.neo4j.driver.Driver
import org.neo4j.driver.Value
import org.neo4j.driver.Values
import org.springframework.beans.factory.BeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.neo4j.core.Neo4jOperations
import org.springframework.data.neo4j.core.ReactiveDatabaseSelectionProvider
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.ReactiveNeo4jTemplate
import org.springframework.data.neo4j.core.convert.Neo4jPersistentPropertyConverter
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import org.springframework.data.neo4j.core.transaction.ReactiveNeo4jTransactionManager
import java.util.*

/**
 * Name for the bean which provides an instance of  [GraphglueNeo4jOperations]
 */
const val GRAPHGLUE_NEO4J_OPERATIONS_BEAN_NAME = "graphglueNeo4jOperations"

/***
 * Autoconfiguration associated with Neo4j part of library
 */
@Configuration
class GraphglueDataConfiguration {

    /**
     * Generates the converter which is used to inject the [LazyLoadingContext] into nodes
     *
     * @param lazyLoadingContext the context used for lazy loading
     * @return the converter
     */
    @Bean("lazyLoadingContextConverter")
    fun lazyLoadingContextConverter(lazyLoadingContext: LazyLoadingContext): Neo4jPersistentPropertyConverter<Optional<LazyLoadingContext>> {
        return object : Neo4jPersistentPropertyConverter<Optional<LazyLoadingContext>> {
            override fun write(source: Optional<LazyLoadingContext>): Value {
                return Values.value(0)
            }

            override fun read(source: Value): Optional<LazyLoadingContext> {
                return Optional.of(lazyLoadingContext)
            }

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
        neo4jClient: ReactiveNeo4jClient,
        neo4jMappingContext: Neo4jMappingContext,
        nodeQueryParser: NodeQueryParser
    ): LazyLoadingContext {
        return LazyLoadingContext(neo4jClient, neo4jMappingContext, nodeQueryParser)
    }

    /**
     * Creates a [ReactiveNeo4jTransactionManager] to provide transaction functionality
     *
     * @param driver the driver for the Neo4j database
     * @param databaseNameProvider provides the name of the database
     */
    @Bean
    @ConditionalOnMissingBean
    fun reactiveTransactionManager(
        driver: Driver,
        databaseNameProvider: ReactiveDatabaseSelectionProvider
    ): ReactiveNeo4jTransactionManager {
        return ReactiveNeo4jTransactionManager(driver, databaseNameProvider)
    }

    /***
     * Bean to provide [Neo4jOperations] which support save over lazy loaded relations
     *
     * @param neo4jTemplate template which provides base operation functionality
     * @param neo4jClient client used to execute queries
     * @param beanFactory used to get the [NodeDefinitionCollection]
     * @return the created [GraphglueNeo4jOperations] which supports save of lazy loaded relations
     */
    @Bean(GRAPHGLUE_NEO4J_OPERATIONS_BEAN_NAME)
    fun graphGlueNeo4jOperations(
        neo4jTemplate: ReactiveNeo4jTemplate,
        neo4jClient: ReactiveNeo4jClient,
        beanFactory: BeanFactory
    ) = GraphglueNeo4jOperations(neo4jTemplate, neo4jClient, beanFactory)

    /***
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
    ): AuthorizationChecker = AuthorizationCheckerImpl(collection, client)
}
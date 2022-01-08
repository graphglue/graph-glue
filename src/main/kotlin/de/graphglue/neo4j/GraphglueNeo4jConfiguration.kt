package de.graphglue.neo4j

import de.graphglue.neo4j.execution.QueryParser
import de.graphglue.neo4j.repositories.GraphglueNeo4jOperations
import org.neo4j.driver.Driver
import org.neo4j.driver.Value
import org.neo4j.driver.Values
import org.springframework.beans.factory.BeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.neo4j.core.ReactiveDatabaseSelectionProvider
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.ReactiveNeo4jTemplate
import org.springframework.data.neo4j.core.convert.Neo4jPersistentPropertyConverter
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import org.springframework.data.neo4j.core.transaction.ReactiveNeo4jTransactionManager
import java.util.*


@Configuration
class GraphglueNeo4jConfiguration {

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

    @Bean
    fun lazyLoadingContext(
        neo4jClient: ReactiveNeo4jClient,
        neo4jMappingContext: Neo4jMappingContext,
        queryParser: QueryParser
    ): LazyLoadingContext {
        return LazyLoadingContext(neo4jClient, neo4jMappingContext, queryParser)
    }

    @Bean
    @ConditionalOnMissingBean
    fun reactiveTransactionManager(
        driver: Driver,
        databaseNameProvider: ReactiveDatabaseSelectionProvider
    ): ReactiveNeo4jTransactionManager? {
        return ReactiveNeo4jTransactionManager(driver, databaseNameProvider)
    }

    @Bean("graphglueNeo4jOperations")
    fun graphGlueNeo4jOperations(
        neo4jTemplate: ReactiveNeo4jTemplate,
        neo4jClient: ReactiveNeo4jClient,
        beanFactory: BeanFactory
    ) = GraphglueNeo4jOperations(neo4jTemplate, neo4jClient, beanFactory)
}
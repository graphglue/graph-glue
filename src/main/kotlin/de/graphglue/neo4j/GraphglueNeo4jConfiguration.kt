package de.graphglue.neo4j

import de.graphglue.graphql.execution.QueryParser
import org.neo4j.driver.Value
import org.neo4j.driver.Values
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.neo4j.core.Neo4jClient
import org.springframework.data.neo4j.core.convert.Neo4jPersistentPropertyConverter
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
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
        neo4jClient: Neo4jClient,
        neo4jMappingContext: Neo4jMappingContext,
        queryParser: QueryParser
    ): LazyLoadingContext {
        return LazyLoadingContext(neo4jClient, neo4jMappingContext, queryParser)
    }
}
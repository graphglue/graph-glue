package com.nkcoding.graphglue.neo4j

import org.neo4j.driver.Value
import org.neo4j.driver.Values
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.neo4j.core.convert.Neo4jPersistentPropertyConverter
import java.util.*

@Configuration
class GraphglueNeo4jConfiguration {

    @Bean("applicationContextConverter")
    fun applicationContextConverter(applicationContext: ApplicationContext): Neo4jPersistentPropertyConverter<Optional<ApplicationContext>> {
        return object: Neo4jPersistentPropertyConverter<Optional<ApplicationContext>> {
            override fun write(source: Optional<ApplicationContext>): Value {
                return Values.value(0)
            }

            override fun read(source: Value): Optional<ApplicationContext> {
                return Optional.of(applicationContext)
            }

        }
    }
}
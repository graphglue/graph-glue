package io.github.graphglue.model

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.neo4j.core.support.UUIDStringGenerator

/**
 * Configuration of model related beans
 */
@Configuration
class GraphglueModelConfiguration {

    /**
     * Default node id generator
     *
     * @return the generator for node ids
     */
    @Bean(NODE_ID_GENERATOR_BEAN)
    @ConditionalOnMissingBean
    fun nodeIdGenerator() = UUIDStringGenerator()

}
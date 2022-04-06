package io.github.graphglue.model

import io.github.graphglue.authorization.AuthorizationRuleGenerator
import org.neo4j.cypherdsl.core.Conditions
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

    /**
     * Bean for a rule which allows all access
     *
     * @return the generated bean
     */
    @Bean(ALL_RULE)
    fun allRuleGenerator() = AuthorizationRuleGenerator { _, _, _ ->
        Conditions.isTrue()
    }
}
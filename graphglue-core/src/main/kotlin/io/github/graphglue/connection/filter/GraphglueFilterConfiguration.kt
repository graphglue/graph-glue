package io.github.graphglue.connection.filter

import io.github.graphglue.connection.filter.generator.AdditionalFilterNodeFilterGenerator
import io.github.graphglue.connection.filter.generator.MetaNodeFilterGenerator
import io.github.graphglue.connection.filter.generator.PropertiesNodeFilterGenerator
import io.github.graphglue.connection.filter.generator.SubtypeNodeFilterGenerator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Provides default [NodeFilterGenerator]s
 */
@Configuration
class GraphglueFilterConfiguration {

    /**
     * Provides the [NodeFilterGenerator] which generates filter entries for properties
     *
     * @return the [NodeFilterGenerator] which generates filter entries for properties
     */
    @Bean
    @ConditionalOnMissingBean
    fun propertiesNodeFilterGenerator() = PropertiesNodeFilterGenerator()

    /**
     * Provides the [NodeFilterGenerator] which generates filter entries for additional filter annotations
     *
     * @return the [NodeFilterGenerator] which generates filter entries for additional filter annotations
     */
    @Bean
    @ConditionalOnMissingBean
    fun additionalFilterNodeFilterGenerator() = AdditionalFilterNodeFilterGenerator()

    /**
     * Provides the [NodeFilterGenerator] which generates filter entries for subtypes
     *
     * @return the [NodeFilterGenerator] which generates filter entries for subtypes
     */
    @Bean
    @ConditionalOnMissingBean
    fun subtypeNodeFilterGenerator() = SubtypeNodeFilterGenerator()

    /**
     * Provides the [NodeFilterGenerator] which generates filter entries for aggregated filters
     *
     * @return the [NodeFilterGenerator] which generates filter entries for aggregated filters
     */
    @Bean
    @ConditionalOnMissingBean
    fun metaNodeFilterGenerator() = MetaNodeFilterGenerator()

}
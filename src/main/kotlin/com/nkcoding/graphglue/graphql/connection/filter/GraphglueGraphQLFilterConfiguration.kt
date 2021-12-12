package com.nkcoding.graphglue.graphql.connection.filter

import com.nkcoding.graphglue.graphql.connection.filter.definition.StringFilterDefinition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GraphglueGraphQLFilterConfiguration {

    @Bean
    fun stringFilter() = TypeFilterDefinitionEntry(String::class) { name, _ -> StringFilterDefinition(name) }

}
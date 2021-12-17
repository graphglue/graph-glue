package com.nkcoding.graphglue.graphql.connection.filter

import com.nkcoding.graphglue.graphql.connection.filter.definition.NodeSubFilterDefinition
import com.nkcoding.graphglue.graphql.connection.filter.definition.NodeListFilterDefinition
import com.nkcoding.graphglue.graphql.connection.filter.definition.StringFilterDefinition
import com.nkcoding.graphglue.model.Node
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType

@Configuration
class GraphglueGraphQLFilterConfiguration {

    @Bean
    fun stringFilter() =
        TypeFilterDefinitionEntry(String::class.createType()) { name, _, _ -> StringFilterDefinition(name) }

    @Bean
    fun nodeFilter() = TypeFilterDefinitionEntry(Node::class.createType()) { name, type, subFilterGenerator ->
        NodeSubFilterDefinition(
            name, "Filters for nodes where the related node match this filter", type, subFilterGenerator
        )
    }

    @Bean
    fun nodeListFilter() =
        TypeFilterDefinitionEntry(List::class.createType(listOf(KTypeProjection.covariant(Node::class.createType())))) { name, type, subFilterGenerator ->
            NodeListFilterDefinition(name, type.arguments.first().type!!, subFilterGenerator)
        }

}
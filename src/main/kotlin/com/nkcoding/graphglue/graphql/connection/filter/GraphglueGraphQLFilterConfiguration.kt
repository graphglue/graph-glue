package com.nkcoding.graphglue.graphql.connection.filter

import com.nkcoding.graphglue.graphql.connection.filter.definition.NodeListFilterDefinition
import com.nkcoding.graphglue.graphql.connection.filter.definition.NodeSubFilterDefinition
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
        TypeFilterDefinitionEntry(String::class.createType()) { name, property, parentNodeDefinition, _ ->
            StringFilterDefinition(name, parentNodeDefinition.getNeo4jNameOfProperty(property))
        }

    @Bean
    fun nodeFilter() =
        TypeFilterDefinitionEntry(Node::class.createType()) { name, property, parentNodeDefinition, subFilterGenerator ->
            NodeSubFilterDefinition(
                name,
                "Filters for nodes where the related node match this filter",
                property.returnType,
                subFilterGenerator,
                parentNodeDefinition.getRelationshipDefinitionOfProperty(property)
            )
        }

    @Bean
    fun nodeListFilter() =
        TypeFilterDefinitionEntry(List::class.createType(listOf(KTypeProjection.covariant(Node::class.createType())))) { name, property, parentNodeDefinition, subFilterGenerator ->
            NodeListFilterDefinition(
                name,
                property.returnType.arguments.first().type!!,
                subFilterGenerator,
                parentNodeDefinition.getRelationshipDefinitionOfProperty(property)
            )
        }

}
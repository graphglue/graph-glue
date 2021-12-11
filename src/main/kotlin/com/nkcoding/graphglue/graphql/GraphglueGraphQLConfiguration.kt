package com.nkcoding.graphglue.graphql

import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.hooks.SchemaGeneratorHooks
import com.nkcoding.graphglue.graphql.redirect.REDIRECT_DIRECTIVE_NAME
import com.nkcoding.graphglue.graphql.redirect.rewireFieldType
import com.nkcoding.testing.model.Filter
import com.nkcoding.testing.model.RedirectedLeaf
import com.nkcoding.testing.model.Root
import graphql.Scalars
import graphql.schema.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

/**
 * Configures beans used in combination with graphql-kotlin and graphql-java
 */
@Configuration
class GraphglueGraphQLConfiguration {

    /**
     * Provides the [SchemaGeneratorHooks] for the [SchemaGeneratorConfig]
     */
    @Bean
    @ConditionalOnMissingBean
    fun schemaGeneratorHooks(): SchemaGeneratorHooks {
        return object : SchemaGeneratorHooks {
            override fun onRewireGraphQLType(generatedType: GraphQLSchemaElement, coordinates: FieldCoordinates?, codeRegistry: GraphQLCodeRegistry.Builder): GraphQLSchemaElement {
                val rewiredType = super.onRewireGraphQLType(generatedType, coordinates, codeRegistry)
                return if (rewiredType is GraphQLFieldDefinition) {
                    rewireFieldType(rewiredType, coordinates, codeRegistry)
                } else {
                    return rewiredType
                }
            }

            override fun willGenerateGraphQLType(type: KType): GraphQLType? {
                if (type.jvmErasure == RedirectedLeaf::class) {
                    println(type)
                }
                return super.willGenerateGraphQLType(type)
            }
        }
    }
}
package io.github.graphglue.definition

import io.github.graphglue.GraphglueCoreConfigurationProperties
import io.github.graphglue.model.Node
import org.springframework.beans.factory.BeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * Provides the [NodeDefinitionCollection]
 *
 */
@Configuration
class GraphglueDefinitionConfiguration {

    /**
     * Generates the [NodeDefinitionCollection]
     * Uses the [Neo4jMappingContext] to find the [Node]s
     *
     * @param beanFactory necessary for the [NodeDefinitionCollection]
     * @param neo4jMappingContext used to get all [Node]s
     * @param extensionFieldDefinitions all known [ExtensionFieldDefinition] beans
     * @param configurationProperties the [GraphglueCoreConfigurationProperties]
     *
     * @return the [NodeDefinitionCollection]
     */
    @Suppress("UNCHECKED_CAST")
    @Bean
    fun nodeDefinitionCollection(
        beanFactory: BeanFactory,
        neo4jMappingContext: Neo4jMappingContext,
        extensionFieldDefinitions: Map<String, ExtensionFieldDefinition>,
        configurationProperties: GraphglueCoreConfigurationProperties
    ): NodeDefinitionCollection {
        val nodeDefinitions =
            neo4jMappingContext.managedTypes.map { it.type.kotlin }.filter { it.isSubclassOf(Node::class) }
                .map { it as KClass<out Node> }.associateWith { generateNodeDefinition(it, neo4jMappingContext, extensionFieldDefinitions) }
        return NodeDefinitionCollection(nodeDefinitions, beanFactory, configurationProperties)
    }

}
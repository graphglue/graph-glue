package io.github.graphglue.data

import io.github.graphglue.model.Node
import io.github.graphglue.model.property.BaseNodePropertyDelegate
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.data.neo4j.autoconfigure.DataNeo4jAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.neo4j.aot.Neo4jManagedTypes
import org.springframework.data.neo4j.core.convert.Neo4jConversions
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import org.springframework.data.neo4j.core.mapping.PersistentPropertyCharacteristics
import org.springframework.data.neo4j.core.mapping.PersistentPropertyCharacteristicsProvider
import kotlin.reflect.full.isSuperclassOf

/**
 * Provides the [Neo4jMappingContext] Graphglue needs.
 *
 * This has to be registered before [DataNeo4jAutoConfiguration], as that autoconfiguration provides
 * a [Neo4jMappingContext] which is built without a [PersistentPropertyCharacteristicsProvider], and
 * the first matching bean definition wins.
 */
@AutoConfiguration(before = [DataNeo4jAutoConfiguration::class])
class GraphglueMappingAutoConfiguration {

    /**
     * Ensures that [BaseNodePropertyDelegate]s are ignored
     *
     * @return [PersistentPropertyCharacteristicsProvider] which ensures that [BaseNodePropertyDelegate]s are ignored
     */
    @Bean
    @ConditionalOnMissingBean
    fun persistentPropertyCharacteristicsProvider(): PersistentPropertyCharacteristicsProvider {
        return PersistentPropertyCharacteristicsProvider { property, _ ->
            if (BaseNodePropertyDelegate::class.isSuperclassOf(property.type.kotlin)) {
                PersistentPropertyCharacteristics.treatAsTransient()
            } else {
                PersistentPropertyCharacteristics.useDefaults()
            }
        }
    }

    /**
     * The [Neo4jMappingContext], built with the [PersistentPropertyCharacteristicsProvider]
     *
     * @param managedTypes the entity types found by the entity scan
     * @param neo4jConversions the conversions to use
     * @param persistentPropertyCharacteristicsProvider used to ignore the delegates of node properties
     * @return the [Neo4jMappingContext] to use
     */
    @Bean
    @ConditionalOnMissingBean
    fun neo4jMappingContext(
        managedTypes: Neo4jManagedTypes,
        neo4jConversions: Neo4jConversions,
        persistentPropertyCharacteristicsProvider: PersistentPropertyCharacteristicsProvider
    ): Neo4jMappingContext {
        val context = Neo4jMappingContext.builder()
            .withNeo4jConversions(neo4jConversions)
            .withPersistentPropertyCharacteristicsProvider(persistentPropertyCharacteristicsProvider)
            .build()
        context.setManagedTypes(managedTypes)
        return context
    }
}

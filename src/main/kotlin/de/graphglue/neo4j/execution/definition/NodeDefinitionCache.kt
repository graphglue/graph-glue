package de.graphglue.neo4j.execution.definition

import de.graphglue.model.Node
import de.graphglue.util.CacheMap
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import kotlin.reflect.KClass

class NodeDefinitionCache(
    private val backingCollection: CacheMap<KClass<out Node>, NodeDefinition>,
    private val neo4jMappingContext: Neo4jMappingContext
) {

    fun getOrCreate(kClass: KClass<out Node>): NodeDefinition {
        return backingCollection.computeIfAbsent(kClass) {
            generateNodeDefinition(kClass, neo4jMappingContext)
        }
    }
}
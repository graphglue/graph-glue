package com.nkcoding.graphglue.graphql.execution.definition

import com.nkcoding.graphglue.graphql.extensions.getSimpleName
import com.nkcoding.graphglue.model.Node
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import kotlin.reflect.KClass

class NodeDefinitionCollectionImpl(
    override val backingCollection: MutableMap<KClass<out Node>, NodeDefinition>,
    private val supertypeNodeDefinitionLookup: Map<Set<String>, NodeDefinition>,
    private val neo4jMappingContext: Neo4jMappingContext
) : MutableNodeDefinitionCollection {

    private val definitionsByGraphQLName = backingCollection.mapKeys { it.key.getSimpleName() }

    override fun getOrCreate(kClass: KClass<out Node>): NodeDefinition {
        return backingCollection.computeIfAbsent(kClass) {
            generateNodeDefinition(kClass, neo4jMappingContext)
        }
    }

    override fun getNodeDefinitionsFromGraphQLNames(names: List<String>): List<NodeDefinition> {
        return supertypeNodeDefinitionLookup[names.toSet()]?.let { listOf(it) }
            ?: names.map { definitionsByGraphQLName[it]!! }
    }
}
package de.graphglue.neo4j.execution.definition

import de.graphglue.graphql.extensions.getSimpleName
import de.graphglue.model.Node
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

class NodeDefinitionCollection(
    backingCollection: Map<KClass<out Node>, NodeDefinition>,

    ) {
    private val backingCollection = HashMap(backingCollection)
    private val definitionsByGraphQLName = backingCollection.mapKeys { it.key.getSimpleName() }
    private val supertypeNodeDefinitionLookup: Map<Set<String>, NodeDefinition>

    init {
        val supertypeNodeDefinitionLookup = mutableMapOf<Set<String>, NodeDefinition>()
        for ((nodeClass, nodeDefinition) in backingCollection) {
            val subTypes = backingCollection.keys.filter { it.isSubclassOf(nodeClass) }
                .filter { !it.isAbstract }
                .map { it.getSimpleName() }
                .toSet()
            supertypeNodeDefinitionLookup[subTypes] = nodeDefinition
        }
        this.supertypeNodeDefinitionLookup = supertypeNodeDefinitionLookup
    }

    fun getNodeDefinitionsFromGraphQLNames(names: List<String>): List<NodeDefinition> {
        return supertypeNodeDefinitionLookup[names.toSet()]?.let { listOf(it) }
            ?: names.map { definitionsByGraphQLName[it]!! }
    }

    fun getNodeDefinition(nodeType: KClass<out Node>): NodeDefinition {
        return backingCollection[nodeType]!!
    }

    inline fun <reified T : Node> getNodeDefinition(): NodeDefinition {
        return getNodeDefinition(T::class)
    }
}
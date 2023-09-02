package io.github.graphglue.data

import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.model.SearchProperty
import org.springframework.data.neo4j.core.Neo4jClient
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Helper to generate the default indices synchronously
 *
 * @param nodeDefinitionCollection the [NodeDefinitionCollection] to use
 * @param neo4jClient the [Neo4jClient] to use
 */
class DefaultIndexCreator(val nodeDefinitionCollection: NodeDefinitionCollection, val neo4jClient: Neo4jClient) {

    /**
     * Creates only the search indices
     */
    fun createSearchIndices() {
        for (nodeDefinition in nodeDefinitionCollection) {
            val searchIndexName = nodeDefinition.searchIndexName
            if (searchIndexName != null) {
                val searchProperties =
                    nodeDefinition.nodeType.memberProperties.filter { it.hasAnnotation<SearchProperty>() }.map {
                        nodeDefinition.persistentEntity.getPersistentProperty(it.name)!!.propertyName
                    }
                val primaryLabel = nodeDefinition.primaryLabel
                neo4jClient.query("CREATE FULLTEXT INDEX $searchIndexName IF NOT EXISTS FOR (n:$primaryLabel) ON EACH [${
                    searchProperties.joinToString(", ") { "n.$it" }
                }]").run()
            }
        }
    }

    /**
     * Creates the default indices
     * This includes an index on the id of all nodes and the search indices
     */
    fun createDefaultIndices() {
        neo4jClient.query("CREATE INDEX IF NOT EXISTS FOR (n:Node) ON (n.id)").run()
        createSearchIndices()
    }

}
package io.github.graphglue.definition

import io.github.graphglue.model.Node
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import org.springframework.data.neo4j.core.mapping.Neo4jPersistentEntity
import kotlin.reflect.KClass

/**
 * Generates a [NodeDefinition] for a specific class
 *
 * @param nodeClass the class to generate the definition for
 * @param mappingContext used to obtain the [Neo4jPersistentEntity]
 * @param extensionFieldDefinitions all known [ExtensionFieldDefinition] beans
 * @return the generated NodeDefinition
 */
fun generateNodeDefinition(
    nodeClass: KClass<out Node>,
    mappingContext: Neo4jMappingContext,
    extensionFieldDefinitions: Map<String, ExtensionFieldDefinition>
): NodeDefinition {
    return NodeDefinition(
        nodeClass, mappingContext.getPersistentEntity(nodeClass.java)!!, extensionFieldDefinitions
    )
}
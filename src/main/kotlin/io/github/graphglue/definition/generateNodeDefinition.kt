package io.github.graphglue.definition

import io.github.graphglue.model.Node
import io.github.graphglue.model.NodeProperty
import io.github.graphglue.model.NodeRelationship
import io.github.graphglue.model.NodeSetProperty
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import org.springframework.data.neo4j.core.mapping.Neo4jPersistentEntity
import kotlin.reflect.KClass
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaField

/**
 * Generates a [NodeDefinition] for a specific class
 *
 * @param nodeClass the class to generate the definition for
 * @param mappingContext used to obtain the [Neo4jPersistentEntity]
 * @return the generated NodeDefinition
 */
fun generateNodeDefinition(nodeClass: KClass<out Node>, mappingContext: Neo4jMappingContext): NodeDefinition {
    return NodeDefinition(
        nodeClass,
        generateOneRelationshipDefinitions(nodeClass),
        generateManyRelationshipDefinitions(nodeClass),
        mappingContext.getPersistentEntity(nodeClass.java)!!
    )
}

/**
 * Generates the [OneRelationshipDefinition]s for a specific [NodeDefinition]
 *
 * @param nodeClass the class to generate the relationship definitions for
 * @return the list of generated relationship definitions
 */
private fun generateOneRelationshipDefinitions(nodeClass: KClass<out Node>): List<OneRelationshipDefinition> {
    val properties = nodeClass.memberProperties.filter {
        it.returnType.isSubtypeOf(Node::class.createType()) && it.returnType.classifier !is KTypeParameter
    }
    return properties.map {
        val field = it.javaField
        if (field == null || !field.type.kotlin.isSubclassOf(NodeProperty::class)) {
            throw NodeSchemaException("Property of type Node is not backed by a NodeProperty: $it")
        }
        val annotation = it.findAnnotation<NodeRelationship>()
            ?: throw NodeSchemaException("Property of type Node is not annotated with NodeRelationship: $it")
        OneRelationshipDefinition(it, annotation.type, annotation.direction, nodeClass)
    }
}

/**
 * Generates the [ManyRelationshipDefinition]s for a specific [NodeDefinition]
 *
 * @param nodeClass the class to generate the relationship definitions for
 * @return the list of generated relationship definitions
 */
private fun generateManyRelationshipDefinitions(nodeClass: KClass<out Node>): List<ManyRelationshipDefinition> {
    val nodeListType =
        NodeSetProperty.NodeSet::class.createType(listOf(KTypeProjection.covariant(Node::class.createType())))
    val properties = nodeClass.memberProperties.filter { it.returnType.isSubtypeOf(nodeListType) }.filter {
            val nodeType = it.returnType.arguments.first().type!!
            nodeType.classifier !is KTypeParameter
        }
    return properties.map {
        val annotation = it.findAnnotation<NodeRelationship>()
            ?: throw NodeSchemaException("Property of type Node is not annotated with NodeRelationship: $it")
        ManyRelationshipDefinition(it, annotation.type, annotation.direction, nodeClass)
    }
}
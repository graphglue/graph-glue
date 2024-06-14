package io.github.graphglue.connection.order

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.definition.OneRelationshipDefinition
import io.github.graphglue.definition.RelationshipDefinition
import io.github.graphglue.graphql.extensions.springFindRepeatableAnnotations
import io.github.graphglue.model.AdditionalOrder
import io.github.graphglue.model.Node
import io.github.graphglue.model.OrderProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Generates a map from order field name (enum casing) to [OrderPart].
 * The [OrderPart]s are detected based on the [OrderProperty] annotation.
 *
 * @param T the type of [Node] for which the [OrderPart]s should be generated
 * @param type the class of [T]
 * @param additionalOrderBeans lookup for additional order parts
 * @return a map from order field enum name to  [OrderPart]
 */
@Suppress("UNCHECKED_CAST")
fun <T : Node> generateOrders(
    type: KClass<T>,
    additionalOrderBeans: Map<String, OrderPart<*>>,
    nodeDefinitionCollection: NodeDefinitionCollection,
    includeComplex: Boolean = true
): Map<String, OrderPart<T>> {
    val nodeDefinition = nodeDefinitionCollection.getNodeDefinition(type)
    val generatedOrders = type.memberProperties.filter { it.hasAnnotation<OrderProperty>() }.flatMap {
        generateOrderPartsForProperty(
            nodeDefinition,
            it,
            includeComplex,
            nodeDefinitionCollection,
            additionalOrderBeans,
        )
    }
    val additionalOrderAnnotations = type.springFindRepeatableAnnotations<AdditionalOrder>()
    val additionalOrders = additionalOrderAnnotations.map {
        additionalOrderBeans[it.beanName]!! as OrderPart<Node>
    }
    val allOrders = additionalOrders + generatedOrders + IdOrderPart
    return allOrders.associateBy { it.name.toEnumNameCase() }
}

/**
 * Generates the [OrderPart]s for a property of a [Node]
 *
 * @param nodeDefinition the definition of the node
 * @param property the property to generate the [OrderPart]s for
 * @param includeComplex whether to include complex properties (relationships) in the order
 * @param nodeDefinitionCollection the collection of all node definitions
 * @param additionalOrderBeans lookup for additional order parts
 * @return the [OrderPart]s for the property (might be multiple for relationships)
 */
private fun <T : Node> generateOrderPartsForProperty(
    nodeDefinition: NodeDefinition,
    property: KProperty1<T, *>,
    includeComplex: Boolean,
    nodeDefinitionCollection: NodeDefinitionCollection,
    additionalOrderBeans: Map<String, OrderPart<*>>,
): List<OrderPart<T>> {
    val relationshipDefinition = nodeDefinition.getRelationshipDefinitionOfPropertyOrNull(property)
    return if (relationshipDefinition != null) {
        if (includeComplex) {
            emptyList()
        } else {
            generateOrderPartsForRelationshipProperty(
                relationshipDefinition, nodeDefinitionCollection, additionalOrderBeans
            )
        }
    } else {
        val neo4jProperty = nodeDefinition.persistentEntity.getPersistentProperty(property.name)
        require(neo4jProperty != null) {
            "Property $property has no corresponding Neo4j property"
        }
        listOf(PropertyOrderPart<T>(property, neo4jProperty.name))
    }
}

/**
 * Generates the [OrderPart]s for a relationship property
 *
 * @param relationshipDefinition the relationship definition
 * @param nodeDefinitionCollection the collection of all node definitions
 * @param additionalOrderBeans lookup for additional order parts
 * @return the [OrderPart]s for the relationship property
 */
private fun <T : Node> generateOrderPartsForRelationshipProperty(
    relationshipDefinition: RelationshipDefinition?,
    nodeDefinitionCollection: NodeDefinitionCollection,
    additionalOrderBeans: Map<String, OrderPart<*>>
): List<OrderPart<T>> {
    require(relationshipDefinition is OneRelationshipDefinition) {
        "Only one relationships are supported for ordering"
    }
    val relatedNodeDefinition = nodeDefinitionCollection.getNodeDefinition(relationshipDefinition.nodeKClass)
    val relatedOrderParts = generateOrders(
        relationshipDefinition.nodeKClass, additionalOrderBeans, nodeDefinitionCollection, includeComplex = false
    )
    return relatedOrderParts.values.map { relatedOrderPart ->
        RelationshipOrderPart(
            relationshipDefinition, relatedOrderPart, relatedNodeDefinition
        )
    }
}

/**
 * Expects a camelCase [String], inserts a `_` before each capital letter, and then makes
 * all letters uppercase
 *
 * @return the [String] in enum casing
 */
private fun String.toEnumNameCase() = this.replace("(?=[A-Z])".toRegex(), "_").uppercase()
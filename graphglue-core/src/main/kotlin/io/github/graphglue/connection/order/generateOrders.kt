package io.github.graphglue.connection.order

import io.github.graphglue.graphql.extensions.springFindRepeatableAnnotations
import io.github.graphglue.model.AdditionalOrder
import io.github.graphglue.model.Node
import io.github.graphglue.model.OrderProperty
import org.springframework.data.neo4j.core.mapping.Neo4jPersistentEntity
import kotlin.reflect.KClass
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Generates a map from order field name (enum casing) to [OrderPart].
 * The [OrderPart]s are detected based on the [OrderProperty] annotation.
 *
 * @param T the type of [Node] for which the [OrderPart]s should be generated
 * @param type the class of [T]
 * @param persistentEntity used to get the name of the property in the database
 * @param additionalOrderBeans lookup for additional order parts
 * @return a map from order field enum name to  [OrderPart]
 */
@Suppress("UNCHECKED_CAST")
fun <T : Node> generateOrders(
    type: KClass<T>,
    persistentEntity: Neo4jPersistentEntity<*>,
    additionalOrderBeans: Map<String, OrderPart<*>>
): Map<String, OrderPart<T>> {
    val generatedOrders = type.memberProperties.filter { it.hasAnnotation<OrderProperty>() }
        .map {
            val neo4jPropertyName = persistentEntity.getPersistentProperty(it.name)!!.propertyName
            PropertyOrderPart<T>(it, neo4jPropertyName)
        }
    val additionalOrderAnnotations = type.springFindRepeatableAnnotations<AdditionalOrder>()
    val additionalOrders = additionalOrderAnnotations.map {
        additionalOrderBeans[it.beanName]!! as OrderPart<Node>
    }
    val allOrders = additionalOrders + generatedOrders + IdOrderPart
    return allOrders.associateBy { it.name.toEnumNameCase() }
}

/**
 * Expects a camelCase [String], inserts a `_` before each capital letter, and then makes
 * all letters uppercase
 *
 * @return the [String] in enum casing
 */
private fun String.toEnumNameCase() = this.replace("(?=[A-Z])".toRegex(), "_").uppercase()
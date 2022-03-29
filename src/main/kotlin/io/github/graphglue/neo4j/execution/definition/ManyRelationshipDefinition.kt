package io.github.graphglue.neo4j.execution.definition

import io.github.graphglue.model.Direction
import io.github.graphglue.model.Node
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.jvmErasure

/**
 * Defines the many side of a many-to-many or many-to-one relationship between two [Node]s
 *
 * @param property the property on the class which defines the relationship
 * @param type the type of the relation (label associated with Neo4j relationship)
 * @param direction direction of the relation (direction associated with Neo4j relationship)
 * @param parentKClass the class associated with the [NodeDefinition] this is used as part of,
 *                        must be a subclass of the property defining class
 */
class ManyRelationshipDefinition(
    property: KProperty1<*, *>,
    type: String,
    direction: Direction,
    parentKClass: KClass<out Node>
) : RelationshipDefinition(
    property,
    @Suppress("UNCHECKED_CAST") (property.returnType.arguments.first().type!!.jvmErasure as KClass<out Node>),
    type,
    direction,
    parentKClass
)
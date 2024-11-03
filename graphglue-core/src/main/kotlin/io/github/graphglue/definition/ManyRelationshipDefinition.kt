package io.github.graphglue.definition

import io.github.graphglue.definition.extensions.firstTypeArgument
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
 *                     must be a subclass of the property defining class
 * @param allowedAuthorizations the names of authorizations which allow via this relation.
 *                              These names result in properties with value `true` on the relation
 */
class ManyRelationshipDefinition(
    property: KProperty1<*, *>,
    type: String,
    direction: Direction,
    parentKClass: KClass<out Node>,
    allowedAuthorizations: Set<String>
) : RelationshipDefinition(
    property,
    @Suppress("UNCHECKED_CAST") (property.returnType.firstTypeArgument.jvmErasure as KClass<out Node>),
    type,
    direction,
    parentKClass,
    allowedAuthorizations
)
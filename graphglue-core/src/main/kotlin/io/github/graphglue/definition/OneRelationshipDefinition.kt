package io.github.graphglue.definition

import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLTypeReference
import io.github.graphglue.definition.extensions.firstTypeArgument
import io.github.graphglue.graphql.extensions.getSimpleName
import io.github.graphglue.graphql.schema.SchemaTransformationContext
import io.github.graphglue.model.Direction
import io.github.graphglue.model.GraphQLNullable
import io.github.graphglue.model.Node
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.jvmErasure

/**
 * Defines the one side of a one-to-one or many-to-one relationship between two [Node]s
 *
 * @param property the property on the class which defines the relationship
 * @param type the type of the relation (label associated with Neo4j relationship)
 * @param direction direction of the relation (direction associated with Neo4j relationship)
 * @param parentKClass the class associated with the [NodeDefinition] this is used as part of,
 *                     must be a subclass of the property defining class
 * @param allowedAuthorizations the names of authorizations which allow via this relation.
 *                              These names result in properties with value `true` on the relation
 */
class OneRelationshipDefinition(
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
) {
    override fun generateFieldDefinition(transformationContext: SchemaTransformationContext): GraphQLFieldDefinition {
        val type = property.returnType.firstTypeArgument
        val graphQLType = GraphQLTypeReference(type.jvmErasure.getSimpleName()).let {
            if (type.isMarkedNullable || property.hasAnnotation<GraphQLNullable>()) {
                it
            } else {
                GraphQLNonNull(it)
            }
        }
        return GraphQLFieldDefinition.newFieldDefinition().name(graphQLName).description(graphQLDescription)
            .type(graphQLType).build()
    }
}
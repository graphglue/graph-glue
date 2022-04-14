package io.github.graphglue.definition

import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLTypeReference
import io.github.graphglue.graphql.SchemaTransformationContext
import io.github.graphglue.graphql.extensions.getSimpleName
import io.github.graphglue.model.Direction
import io.github.graphglue.model.Node
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.jvmErasure

/**
 * Defines the one side of a one-to-one or many-to-one relationship between two [Node]s
 *
 * @param property the property on the class which defines the relationship
 * @param type the type of the relation (label associated with Neo4j relationship)
 * @param direction direction of the relation (direction associated with Neo4j relationship)
 * @param parentKClass the class associated with the [NodeDefinition] this is used as part of,
 *                        must be a subclass of the property defining class
 */
class OneRelationshipDefinition(
    property: KProperty1<*, *>, type: String, direction: Direction, parentKClass: KClass<out Node>
) : RelationshipDefinition(
    property,
    @Suppress("UNCHECKED_CAST") (property.returnType.jvmErasure as KClass<out Node>),
    type,
    direction,
    parentKClass
) {
    override fun generateFieldDefinition(transformationContext: SchemaTransformationContext): GraphQLFieldDefinition {
        val type = property.returnType
        val graphQLType = GraphQLTypeReference(type.jvmErasure.getSimpleName()).let {
            if (type.isMarkedNullable) {
                it
            } else {
                GraphQLNonNull(it)
            }
        }
        return GraphQLFieldDefinition.newFieldDefinition().name(graphQLName).description(graphQLDescription)
            .type(graphQLType).build()
    }
}
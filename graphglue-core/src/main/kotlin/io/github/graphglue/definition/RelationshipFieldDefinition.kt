package io.github.graphglue.definition

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import io.github.graphglue.graphql.extensions.getPropertyName
import kotlin.reflect.KVisibility
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

/**
 * Base class for [OneRelationshipFieldDefinition] and [ManyRelationshipFieldDefinition]
 *
 * @param T the type of the contained [RelationshipDefinition]
 * @param relationshipDefinition contained [T] which defines what subquery to build
 */
abstract class RelationshipFieldDefinition<T : RelationshipDefinition>(
    val relationshipDefinition: T
) : FieldDefinition(relationshipDefinition.property) {

    override val graphQLName get() = property!!.getPropertyName(relationshipDefinition.parentKClass)

    /**
     * Description of the property
     */
    val graphQLDescription get() = property!!.findAnnotation<GraphQLDescription>()?.value

    /**
     * If true, this exposes a field in the GraphQL API
     */
    val isGraphQLVisible get() = property!!.visibility == KVisibility.PUBLIC && !property.hasAnnotation<GraphQLIgnore>()

}
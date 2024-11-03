package io.github.graphglue.definition

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLTypeReference
import io.github.graphglue.data.LazyLoadingContext
import io.github.graphglue.data.execution.FieldFetchingContext
import io.github.graphglue.data.execution.NodeQueryEntry
import io.github.graphglue.data.execution.NodeQueryParser
import io.github.graphglue.data.execution.NodeQueryResult
import io.github.graphglue.definition.extensions.firstTypeArgument
import io.github.graphglue.graphql.extensions.getSimpleName
import io.github.graphglue.graphql.schema.SchemaTransformationContext
import io.github.graphglue.model.GraphQLNullable
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.jvmErasure

/**
 * Field based on a [OneRelationshipDefinition], represented as the node in GraphQL
 *
 * @param relationshipDefinition contained [OneRelationshipDefinition] which defines what subquery to build
 */
class OneRelationshipFieldDefinition(
    relationshipDefinition: OneRelationshipDefinition
) : RelationshipFieldDefinition<OneRelationshipDefinition>(relationshipDefinition) {

    override fun createGraphQLResult(result: Any?, lazyLoadingContext: LazyLoadingContext): Any? {
        return (result as NodeQueryResult<*>).nodes.firstOrNull()
    }

    override fun createQueryEntry(
        dfe: DataFetchingEnvironment,
        context: FieldFetchingContext,
        nodeQueryParser: NodeQueryParser,
        onlyOnTypes: List<NodeDefinition>?
    ): NodeQueryEntry<*> {
        return nodeQueryParser.generateSubQuery(this, listOf(this.relationshipDefinition), context, dfe, onlyOnTypes)
    }

    override fun generateFieldDefinition(transformationContext: SchemaTransformationContext): GraphQLFieldDefinition? {
        if (!isGraphQLVisible) {
            return null
        }
        val type = property!!.returnType.firstTypeArgument
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
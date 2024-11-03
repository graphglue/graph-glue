package io.github.graphglue.definition

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import io.github.graphglue.connection.generateConnectionFieldDefinition
import io.github.graphglue.connection.model.Connection
import io.github.graphglue.data.LazyLoadingContext
import io.github.graphglue.data.execution.FieldFetchingContext
import io.github.graphglue.data.execution.NodeQueryEntry
import io.github.graphglue.data.execution.NodeQueryParser
import io.github.graphglue.data.execution.NodeQueryResult
import io.github.graphglue.definition.extensions.firstTypeArgument
import io.github.graphglue.graphql.schema.SchemaTransformationContext
import io.github.graphglue.model.Node
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmErasure

/**
 * Field based on a [ManyRelationshipDefinition], represented as the connection in GraphQL
 *
 * @param relationshipDefinition contained [ManyRelationshipDefinition] which defines what subquery to build
 */
class ManyRelationshipFieldDefinition(
    relationshipDefinition: ManyRelationshipDefinition
) : RelationshipFieldDefinition<ManyRelationshipDefinition>(relationshipDefinition) {

    override fun createGraphQLResult(result: Any?, lazyLoadingContext: LazyLoadingContext): Any {
        return Connection.fromQueryResult(result as NodeQueryResult<*>, lazyLoadingContext.nodeQueryParser.objectMapper)
    }

    override fun createQueryEntry(
        dfe: DataFetchingEnvironment,
        context: FieldFetchingContext,
        nodeQueryParser: NodeQueryParser,
        onlyOnTypes: List<NodeDefinition>?
    ): NodeQueryEntry<*> {
        return nodeQueryParser.generateSubQuery(this, listOf(this.relationshipDefinition), context, dfe, onlyOnTypes)
    }

    @Suppress("UNCHECKED_CAST")
    override fun generateFieldDefinition(transformationContext: SchemaTransformationContext): GraphQLFieldDefinition? {
        if (!isGraphQLVisible) {
            return null
        }
         val returnNodeType = property!!.returnType.firstTypeArgument.jvmErasure as KClass<out Node>
        return generateConnectionFieldDefinition(returnNodeType, graphQLName, graphQLDescription, transformationContext)
    }

}
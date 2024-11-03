package io.github.graphglue.definition

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import io.github.graphglue.data.LazyLoadingContext
import io.github.graphglue.data.execution.FieldFetchingContext
import io.github.graphglue.data.execution.NodeExtensionField
import io.github.graphglue.data.execution.NodeQueryEntry
import io.github.graphglue.data.execution.NodeQueryParser
import io.github.graphglue.graphql.schema.SchemaTransformationContext
import org.neo4j.cypherdsl.core.Expression
import org.neo4j.cypherdsl.core.Node
import org.neo4j.driver.Value
import kotlin.reflect.KProperty1

/**
 * Definition of an extension field on a [Node]
 *
 * @param field the field definition in the GraphQL schema
 * @param cost the cost of this field, defaults to 0
 */
abstract class ExtensionFieldDefinition(
    val field: GraphQLFieldDefinition, val cost: Int = 0
) : FieldDefinition(null) {

    /**
     * The name of the field in the GraphQL schema
     */
    override val graphQLName = field.name

    /**
     * Generates the Cypher expression used to fetch the field
     *
     * @param dfe the data fetching environment, caution: NOT SPECIFIC TO THIS FIELD
     * @param arguments arguments provided for the field
     * @param node the current node
     * @param nodeDefinition the definition of the current node
     * @return the generated expression
     */
    abstract fun generateFetcher(
        dfe: DataFetchingEnvironment, arguments: Map<String, Any?>, node: Node, nodeDefinition: NodeDefinition
    ): Expression

    /**
     * Transforms the result fetched from the database to the result returned via the GraphQL API
     * By default, this is the identity function
     *
     * @param result the result of the Cypher query
     * @return the transformed result
     */
    abstract fun transformResult(result: Value): Any

    override fun createGraphQLResult(result: Any?, lazyLoadingContext: LazyLoadingContext): Any? {
        return result
    }

    override fun createQueryEntry(
        dfe: DataFetchingEnvironment,
        context: FieldFetchingContext,
        nodeQueryParser: NodeQueryParser,
        onlyOnTypes: List<NodeDefinition>?
    ): NodeQueryEntry<*> {
        return NodeExtensionField(this, dfe, context.arguments, onlyOnTypes, context.resultKeyPath)
    }

    override fun generateFieldDefinition(transformationContext: SchemaTransformationContext): GraphQLFieldDefinition? {
        return field
    }
}
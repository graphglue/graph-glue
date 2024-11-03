package io.github.graphglue.definition

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import io.github.graphglue.data.LazyLoadingContext
import io.github.graphglue.data.execution.FieldFetchingContext
import io.github.graphglue.data.execution.NodeQueryEntry
import io.github.graphglue.data.execution.NodeQueryParser
import io.github.graphglue.graphql.schema.SchemaTransformationContext
import kotlin.reflect.KProperty1

/**
 * Definition of a field on the node
 *
 * @param property optional property for cache initialization
 */
abstract class FieldDefinition(
    val property: KProperty1<*, *>?
) {

    /**
     * The name of the field in the GraphQL schema
     */
    abstract val graphQLName: String

    /**
     * Creates the query part entry which can be used to fetch this field
     *
     * @param dfe data fetching environment, not necessarily of this field (should only be used for global context)
     * @param context provides the arguments and selection set for the field
     * @param nodeQueryParser provides access to the query parser
     * @param onlyOnTypes a list of parent types on which this should be evaluated
     */
    abstract fun createQueryEntry(
        dfe: DataFetchingEnvironment,
        context: FieldFetchingContext,
        nodeQueryParser: NodeQueryParser,
        onlyOnTypes: List<NodeDefinition>?
    ): NodeQueryEntry<*>

    /**
     * Creates the field result for a graphql query
     *
     * @param result the result of the database query, might be cached or newly queried
     * @param lazyLoadingContext context providing access to the query parser and query engine
     * @return the field result
     */
    abstract fun createGraphQLResult(
        result: Any?,
        lazyLoadingContext: LazyLoadingContext
    ): Any?

    /**
     * Generates a field definition
     *
     * @param transformationContext used to generate GraphQL types, register data fetchers, ...
     * @return the generated field definition, or null if the field should not be exposed
     */
    internal abstract fun generateFieldDefinition(transformationContext: SchemaTransformationContext): GraphQLFieldDefinition?
}
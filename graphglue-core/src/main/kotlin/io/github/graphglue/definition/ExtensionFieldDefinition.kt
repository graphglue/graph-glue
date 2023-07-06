package io.github.graphglue.definition

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.SelectedField
import io.github.graphglue.model.Node
import org.neo4j.cypherdsl.core.Expression
import org.neo4j.cypherdsl.core.SymbolicName
import org.neo4j.driver.Value

/**
 * Definition of an extension field on a [Node]
 *
 * @param field the field definition in the GraphQL schema
 */
abstract class ExtensionFieldDefinition(val field: GraphQLFieldDefinition) {

    /**
     * The name of the field in the GraphQL schema
     */
    val graphQLName get() = field.name

    /**
     * Generates the Cypher expression used to fetch the field
     *
     * @param dfe the data fetching environment, caution: NOT SPECIFIC TO THIS FIELD
     * @param arguments arguments provided for the field
     * @param node the name of the current node
     * @return the generated expression
     */
    abstract fun generateFetcher(dfe: DataFetchingEnvironment, arguments: Map<String, Any?>, node: SymbolicName): Expression

    /**
     * Transforms the result fetched from the database to the result returned via the GraphQL API
     * By default, this is the identity function
     *
     * @param result the result of the Cypher query
     * @return the transformed result
     */
    abstract fun transformResult(result: Value): Any

}
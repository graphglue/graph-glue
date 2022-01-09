package de.graphglue.graphql.extensions

import com.expediagroup.graphql.generator.extensions.deepName
import de.graphglue.neo4j.authorization.AUTHORIZATION_GRAPHQL_CONTEXT_ENTRY_NAME
import de.graphglue.neo4j.authorization.AuthorizationContext
import de.graphglue.neo4j.execution.NodeQuery
import de.graphglue.neo4j.execution.definition.NodeDefinition
import de.graphglue.neo4j.execution.definition.NodeDefinitionCollection
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment

fun DataFetchingEnvironment.getParentNodeDefinition(nodeDefinitionCollection: NodeDefinitionCollection): NodeDefinition {
    val parentTypeName = parentType.deepName
    return nodeDefinitionCollection.getNodeDefinitionsFromGraphQLNames(listOf(parentTypeName)).first()
}

fun <R> DataFetchingEnvironment.getDataFetcherResult(
    result: R,
    partId: String
): DataFetcherResult<R> {
    val nodeQuery = getLocalContext<NodeQuery>()
    return if (nodeQuery != null) {
        DataFetcherResult.newResult<R>()
            .data(result)
            .localContext(nodeQuery.parts[partId])
            .build()
    } else {
        DataFetcherResult.newResult<R>()
            .data(result)
            .build()
    }
}

val DataFetchingEnvironment.authorizationContext: AuthorizationContext? get() {
    return this.graphQlContext.get<AuthorizationContext?>(AUTHORIZATION_GRAPHQL_CONTEXT_ENTRY_NAME)
}
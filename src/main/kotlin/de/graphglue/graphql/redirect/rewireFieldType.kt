package de.graphglue.graphql.redirect

import com.expediagroup.graphql.generator.extensions.unwrapType
import de.graphglue.model.NODE_RELATIONSHIP_DIRECTIVE
import graphql.schema.*

/**
 * Redirects fields used for NodeSetProperties
 *
 * @param fieldDefinition the field to maybe redirect
 * @return the new field definition
 */
fun rewireFieldType(fieldDefinition: GraphQLFieldDefinition): GraphQLFieldDefinition {
    if (fieldDefinition.hasDirective(NODE_RELATIONSHIP_DIRECTIVE)) {
        val fieldType = fieldDefinition.type
        val originalType = fieldType.unwrapType() as GraphQLObjectType
        val redirectedFunction = originalType.getField("getFromGraphQL")
        if (redirectedFunction != null) {
            return fieldDefinition.transform {
                it.type(redirectedFunction.type)
                it.replaceArguments(redirectedFunction.arguments)
            }
        }
    }
    return fieldDefinition
}

/***
 * Wrapper for an existing [DataFetchingEnvironment] but which replaces the source
 * by applying the original DataFetchingEnvironment first on the original [parentDataFetcher]
 */
private class RedirectDataFetchingEnvironment(
    private val parent: DataFetchingEnvironment,
    private val parentDataFetcher: DataFetcher<*>
) : DataFetchingEnvironment by parent {
    override fun <T : Any?> getSource(): T {
        @Suppress("UNCHECKED_CAST")
        return parentDataFetcher.get(parent) as T
    }
}
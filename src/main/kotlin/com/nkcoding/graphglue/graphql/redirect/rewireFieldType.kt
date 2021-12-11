package com.nkcoding.graphglue.graphql.redirect

import graphql.schema.*

/**
 * Redirects a field type is necessary
 * Does currently not handle the necessary DataFetcher adoption
 *
 * @param fieldDefinition the field to maybe redirect
 * @param coordinates the coordinates of the field to maybe redirect
 * @param codeRegistry used to get and set data fetching environments
 * @return the new field definition
 */
fun rewireFieldType(
    fieldDefinition: GraphQLFieldDefinition,
    coordinates: FieldCoordinates?,
    codeRegistry: GraphQLCodeRegistry.Builder
): GraphQLFieldDefinition {
    val fieldType = fieldDefinition.type
    if (fieldType is GraphQLNonNull) {
        val originalType = fieldType.wrappedType
        if (originalType is GraphQLObjectType && originalType.hasDirective(REDIRECT_DIRECTIVE_NAME)) {
            val redirectedFunction = originalType.fields.first { it.hasDirective(REDIRECT_DIRECTIVE_NAME) }
            val redirectedField = fieldDefinition.transform {
                it.type(redirectedFunction.type)
                it.replaceArguments(redirectedFunction.arguments)
            }
            val functionDataFetcher = codeRegistry.getDataFetcher(originalType, redirectedFunction)
            val propertyDataFetcher = codeRegistry.getDataFetcher(coordinates, fieldDefinition)
            codeRegistry.dataFetcher(coordinates, DataFetcher {
                functionDataFetcher.get(RedirectDataFetchingEnvironment(it, propertyDataFetcher))
            })
            return redirectedField
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
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getSource(): T {
        return parentDataFetcher.get(parent) as T
    }
}
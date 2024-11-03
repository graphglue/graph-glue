package io.github.graphglue.data.execution

import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingFieldSelectionSet
import graphql.schema.SelectedField

/**
 * Context for fetching entries in a node query
 *
 * @param arguments for the current field
 * @param selectionSet sub-selection for the current field
 * @param resultKeyPath path to the current field
 */
class FieldFetchingContext(
    val arguments: Map<String, Any>, val selectionSet: DataFetchingFieldSelectionSet, val resultKeyPath: String
) {
    companion object {
        /**
         * Creates a new [FieldFetchingContext] based on a [DataFetchingEnvironment]
         *
         * @param dataFetchingEnvironment provides arguments, sub-selection and a current result key path
         * @return the generated [FieldFetchingContext]
         */
        fun from(dataFetchingEnvironment: DataFetchingEnvironment): FieldFetchingContext {
            return FieldFetchingContext(
                dataFetchingEnvironment.arguments,
                dataFetchingEnvironment.selectionSet,
                dataFetchingEnvironment.executionStepInfo.path.keysOnly.joinToString("/")
            )
        }
    }

    /**
     * Creates a new [FieldFetchingContext] based on a [field]
     * Assumes that [field] is a direct sub-field of the currently represented field
     *
     * @param field the subfield the created [FieldFetchingContext] should represent
     * @return a new [FieldFetchingContext] representing [field]
     */
    fun ofField(field: SelectedField): FieldFetchingContext {
        return FieldFetchingContext(
            field.arguments, field.selectionSet, "$resultKeyPath/${field.resultKey}"
        )
    }
}


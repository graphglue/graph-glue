package io.github.graphglue.graphql.schema

import graphql.schema.DataFetchingEnvironment
import io.github.graphglue.definition.FieldDefinition

/**
 * [DataFetchingEnvironment] which also contains the [FieldDefinition] of the field being fetched
 *
 * @param parent the parent [DataFetchingEnvironment]
 * @param fieldDefinition the [FieldDefinition] of the field being fetched
 */
class FieldDataFetchingEnvironment(
    private val parent: DataFetchingEnvironment, internal val fieldDefinition: FieldDefinition
) : DataFetchingEnvironment by parent
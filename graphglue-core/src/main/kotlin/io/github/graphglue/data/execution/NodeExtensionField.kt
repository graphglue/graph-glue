package io.github.graphglue.data.execution

import graphql.schema.DataFetchingEnvironment
import graphql.schema.SelectedField
import io.github.graphglue.definition.ExtensionFieldDefinition
import io.github.graphglue.definition.NodeDefinition

/**
 * Defines an extension field which should be loaded with a NodeQuery
 *
 * @param definition defines the extension field and provides the generator for the expression
 * @param dfe the data fetching environment required to generate the expression
 * @param field the selected field
 * @param onlyOnTypes a list of parent types on which this should be evaluated
 * @param resultKey used to identify the result
 */
class NodeExtensionField(
    val definition: ExtensionFieldDefinition,
    val dfe: DataFetchingEnvironment,
    val field: SelectedField,
    onlyOnTypes: List<NodeDefinition>,
    resultKey: String
) : NodeQueryPartEntry(onlyOnTypes, resultKey)
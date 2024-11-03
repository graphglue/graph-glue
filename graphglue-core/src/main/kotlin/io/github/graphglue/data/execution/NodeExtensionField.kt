package io.github.graphglue.data.execution

import graphql.schema.DataFetchingEnvironment
import io.github.graphglue.definition.ExtensionFieldDefinition
import io.github.graphglue.definition.NodeDefinition

/**
 * Defines an extension field which should be loaded with a NodeQuery
 *
 * @param definition defines the extension field and provides the generator for the expression
 * @param dfe the data fetching environment required to generate the expression
 * @param arguments the arguments provided for this field
 * @param onlyOnTypes a list of parent types on which this should be evaluated
 * @param resultKeyPath path to the key which fetches this field
 */
class NodeExtensionField(
    definition: ExtensionFieldDefinition,
    val dfe: DataFetchingEnvironment,
    val arguments: Map<String, Any>,
    onlyOnTypes: List<NodeDefinition>?,
    resultKeyPath: String,
) : NodeQueryEntry<ExtensionFieldDefinition>(onlyOnTypes, resultKeyPath, definition) {

    override val cost: Int get() = fieldDefinition.cost

}
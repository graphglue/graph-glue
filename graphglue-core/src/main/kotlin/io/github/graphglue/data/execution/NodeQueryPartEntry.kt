package io.github.graphglue.data.execution

import io.github.graphglue.definition.NodeDefinition

/**
 * Subclass for [NodeExtensionField] and [NodeSubQuery]
 */
abstract class NodeQueryPartEntry(
    val onlyOnTypes: List<NodeDefinition>,
    val resultKey: String
)
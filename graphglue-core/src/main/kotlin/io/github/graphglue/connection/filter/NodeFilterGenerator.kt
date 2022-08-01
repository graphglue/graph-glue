package io.github.graphglue.connection.filter

import io.github.graphglue.connection.filter.definition.FilterEntryDefinition
import io.github.graphglue.connection.filter.definition.SubFilterGenerator
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.model.Node

/**
 * Interface which can be used to provide beans which provide additional filter entries
 * for specific node filters.
 * Can be used to generate additional filter entries
 */
fun interface NodeFilterGenerator {

    /**
     * Generate the additional filter entries
     *
     * @param definition the definition of the [Node] to generate the filter entry for
     * @param subFilterGenerator can be used to generate subfilters if necessary
     * @return the generated filter entries, may be empty
     */
    fun generateFilterEntries(
        definition: NodeDefinition, subFilterGenerator: SubFilterGenerator
    ): Collection<FilterEntryDefinition>

}
package io.github.graphglue.connection.filter.definition

import io.github.graphglue.connection.filter.NodeFilterGenerator
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.connection.filter.TypeFilterDefinitionEntry
import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.graphql.extensions.getPropertyName
import io.github.graphglue.model.AdditionalFilter
import io.github.graphglue.model.Node
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.jvmErasure

/**
 * Used to generate filters for properties.
 * Also provides access to the [FilterDefinitionCache], [NodeDefinitionCollection] and a lookup of additional filters.
 *
 * @param filters contains all definitions how filters for specific types are generated
 * @param filterDefinitionCache cache of already generated filters for [Node] types
 * @param nodeDefinitionCollection cache of already generated [NodeDefinition]s
 * @param additionalFilterBeans lookup for filters defined using the [AdditionalFilter] annotation
 * @param nodeFilterGenerators generators for additional filter entries
 */
class SubFilterGenerator(
    private val filters: List<TypeFilterDefinitionEntry>,
    val filterDefinitionCache: FilterDefinitionCache,
    val nodeDefinitionCollection: NodeDefinitionCollection,
    val additionalFilterBeans: Map<String, FilterEntryDefinition>,
    val nodeFilterGenerators: List<NodeFilterGenerator>
) {

    /**
     * Generates a [FilterEntryDefinition] for a specified type with a specified name
     * A filter for the type of the property must be defined in [filters]
     *
     * @param property the property to generate the filter for
     * @param parentNodeDefinition the definition of the [Node] type which contains the `property´
     * @return the generated [FilterEntryDefinition]
     * @throws IllegalStateException if no filter for the property type can be generated
     */
    fun filterForProperty(property: KProperty1<*, *>, parentNodeDefinition: NodeDefinition): FilterEntryDefinition {
        val type = property.returnType
        for (filter in filters) {
            if (type.isSubtypeOf(filter.associatedType)) {
                return filter.filterDefinitionFactory(
                    property.getPropertyName(type.jvmErasure),
                    property,
                    parentNodeDefinition,
                    this
                )
            }
        }
        throw IllegalStateException("Cannot create filter for type $type")
    }
}
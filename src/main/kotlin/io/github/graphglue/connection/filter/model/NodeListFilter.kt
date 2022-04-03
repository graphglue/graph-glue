package io.github.graphglue.connection.filter.model

import io.github.graphglue.connection.filter.definition.NodeListFilterDefinition
import io.github.graphglue.connection.filter.definition.NodeSubFilterDefinition
import org.neo4j.cypherdsl.core.*

/**
 *
 */
class NodeListFilter(definition: NodeListFilterDefinition, entries: List<NodeListFilterEntry>) :
    SimpleObjectFilter(definition, entries)

/**
 * [FilterEntry] used to filter when the entry is a list
 * can be used for filters where either all, any or none of the elements of the list have to
 * match a filter
 *
 * @param subFilterDefinition associated definition of the entry
 * @param filter filter to filter the elements of the list
 */
abstract class NodeListFilterEntry(
    private val subFilterDefinition: NodeSubFilterDefinition,
    val filter: NodeSubFilter
) : FilterEntry(subFilterDefinition) {
    override fun generateCondition(node: Node): Condition {
        val relationshipDefinition = subFilterDefinition.relationshipDefinition
        val relatedNode = Cypher.anyNode(node.requiredSymbolicName.value + "_")
        val relationship = relationshipDefinition.generateRelationship(node, relatedNode)
        return generatePredicate(relatedNode.requiredSymbolicName)
            .`in`(Cypher.listBasedOn(relationship).returning(relatedNode))
            .where(filter.generateCondition(relatedNode))
    }

    /**
     * Generates the predicate which defines how nodes of the list have to match the filter,
     * so that the overall filter evaluates to true
     * Examples include all, any and none
     *
     * @param variable the name of the variable based on which the predicate should be build
     * @return the builder for the predicate
     */
    abstract fun generatePredicate(variable: SymbolicName): Predicates.OngoingListBasedPredicateFunction
}

/**
 * [NodeListFilterEntry] where all of the nodes have to match the filter
 *
 * @param definition associated definition of the entry
 * @param filter filter to filter the elements of the list
 */
class AllNodeListFilterEntry(definition: NodeSubFilterDefinition, filter: NodeSubFilter) :
    NodeListFilterEntry(definition, filter) {
    override fun generatePredicate(variable: SymbolicName) = Predicates.all(variable)
}

/**
 * [NodeListFilterEntry] where any of the nodes have to match the filter
 *
 * @param definition associated definition of the entry
 * @param filter filter to filter the elements of the list
 */
class AnyNodeListFilterEntry(definition: NodeSubFilterDefinition, filter: NodeSubFilter) :
    NodeListFilterEntry(definition, filter) {
    override fun generatePredicate(variable: SymbolicName) = Predicates.any(variable)
}

/**
 * [NodeListFilterEntry] where none of the nodes have to match the filter
 *
 * @param definition associated definition of the entry
 * @param filter filter to filter the elements of the list
 */
class NoneNodeListFilterEntry(definition: NodeSubFilterDefinition, filter: NodeSubFilter) :
    NodeListFilterEntry(definition, filter) {
    override fun generatePredicate(variable: SymbolicName) = Predicates.none(variable)
}
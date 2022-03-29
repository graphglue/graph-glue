package io.github.graphglue.graphql.connection.filter.model

import de.graphglue.graphql.connection.filter.definition.NodeListFilterDefinition
import de.graphglue.graphql.connection.filter.definition.NodeSubFilterDefinition
import org.neo4j.cypherdsl.core.*

class NodeListFilter(definition: NodeListFilterDefinition, entries: List<NodeListFilterEntry>) :
    SimpleObjectFilter(definition, entries)

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

    abstract fun generatePredicate(variable: SymbolicName): Predicates.OngoingListBasedPredicateFunction
}

class AllNodeListFilterEntry(definition: NodeSubFilterDefinition, filter: NodeSubFilter) :
    NodeListFilterEntry(definition, filter) {
    override fun generatePredicate(variable: SymbolicName) = Predicates.all(variable)
}

class AnyNodeListFilterEntry(definition: NodeSubFilterDefinition, filter: NodeSubFilter) :
    NodeListFilterEntry(definition, filter) {
    override fun generatePredicate(variable: SymbolicName) = Predicates.any(variable)
}

class NoneNodeListFilterEntry(definition: NodeSubFilterDefinition, filter: NodeSubFilter) :
    NodeListFilterEntry(definition, filter) {
    override fun generatePredicate(variable: SymbolicName) = Predicates.none(variable)
}
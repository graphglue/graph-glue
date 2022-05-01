package io.github.graphglue.connection.filter.model

import io.github.graphglue.authorization.Permission
import io.github.graphglue.connection.filter.definition.NodeSubFilterDefinition
import org.neo4j.cypherdsl.core.*

/**
 * [FilterEntry] used to filter when the entry is a set
 * can be used for filters where either all, any or none of the elements of the set have to
 * match a filter
 * Automatically includes a condition which checks that the filter does not include any nodes which the
 * provided Permission (if provided) does not allow to access. This prevents information leakage using filters.
 * Without this, it would be possible to get information of related nodes by brute-forcing different filters
 * (e.g. getting one character of a name at a time by checking if the related node is still returned).
 *
 * @param subFilterDefinition associated definition of the entry
 * @param filter filter to filter the elements of the set
 * @param permission the current read permission, used to only consider nodes in filters which match the permission
 */
abstract class NodeRelationshipFilterEntry(
    private val subFilterDefinition: NodeSubFilterDefinition,
    val filter: NodeSubFilter,
    private val permission: Permission?
) : FilterEntry(subFilterDefinition) {
    override fun generateCondition(node: Node): Condition {
        val relationshipDefinition = subFilterDefinition.relationshipDefinition
        val relatedNode = Cypher.anyNode(node.requiredSymbolicName.value + "_")
        val relationship = relationshipDefinition.generateRelationship(node, relatedNode)
        return generatePredicate(relatedNode.requiredSymbolicName).`in`(
            Cypher.listBasedOn(relationship).returning(relatedNode)
        ).where(filter.generateCondition(relatedNode))
    }

    /**
     * Generates the predicate which defines how nodes of the set have to match the filter,
     * so that the overall filter evaluates to true
     * Examples include all, any and none
     *
     * @param variable the name of the variable based on which the predicate should be build
     * @return the builder for the predicate
     */
    abstract fun generatePredicate(variable: SymbolicName): Predicates.OngoingListBasedPredicateFunction
}

/**
 * [NodeRelationshipFilterEntry] where all of the nodes have to match the filter
 *
 * @param definition associated definition of the entry
 * @param filter filter to filter the elements of the set
 * @param permission the current read permission, used to only consider nodes in filters which match the permission
 */
class AllNodeRelationshipFilterEntry(
    definition: NodeSubFilterDefinition, filter: NodeSubFilter, permission: Permission?
) : NodeRelationshipFilterEntry(definition, filter, permission) {
    override fun generatePredicate(variable: SymbolicName) = Predicates.all(variable)
}

/**
 * [NodeRelationshipFilterEntry] where any of the nodes have to match the filter
 *
 * @param definition associated definition of the entry
 * @param filter filter to filter the elements of the set
 * @param permission the current read permission, used to only consider nodes in filters which match the permission
 */
class AnyNodeRelationshipFilterEntry(
    definition: NodeSubFilterDefinition, filter: NodeSubFilter, permission: Permission?
) : NodeRelationshipFilterEntry(definition, filter, permission) {
    override fun generatePredicate(variable: SymbolicName) = Predicates.any(variable)
}

/**
 * [NodeRelationshipFilterEntry] where none of the nodes have to match the filter
 *
 * @param definition associated definition of the entry
 * @param filter filter to filter the elements of the set
 * @param permission the current read permission, used to only consider nodes in filters which match the permission
 */
class NoneNodeRelationshipFilterEntry(
    definition: NodeSubFilterDefinition, filter: NodeSubFilter, permission: Permission?
) : NodeRelationshipFilterEntry(definition, filter, permission) {
    override fun generatePredicate(variable: SymbolicName) = Predicates.none(variable)
}
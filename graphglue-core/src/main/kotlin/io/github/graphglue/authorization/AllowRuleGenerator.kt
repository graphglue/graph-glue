package io.github.graphglue.authorization

import io.github.graphglue.model.Rule
import org.neo4j.cypherdsl.core.*

/**
 * Interface for beans which can create allow authorization conditions for rules
 */
interface AllowRuleGenerator {

    /**
     * Generates a condition for a specific rule
     *
     * @param node the [Node] on which the condition should be applied
     * @param currentRelationship the already existing part of the relationship in the match, can be extended
     * @param rule the [Rule] to generate the condition for
     * @param permission context for condition generation
     * @return the extended [currentRelationship] (or the same if unmodified) and a condition to check
     */
    fun generateRule(
        node: Node,
        currentRelationship: RelationshipPattern,
        rule: Rule,
        permission: Permission
    ): Pair<RelationshipPattern, Condition>
}
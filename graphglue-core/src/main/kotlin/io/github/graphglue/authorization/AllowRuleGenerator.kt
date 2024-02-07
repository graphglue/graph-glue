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
     * @param rule the [Rule] to generate the condition for
     * @param permission context for condition generation
     * @return the relationship or null if no relationship is required and a condition to check
     */
    fun generateRule(
        node: Node,
        rule: Rule,
        permission: Permission
    ): Pair<RelationshipPattern?, Condition>
}
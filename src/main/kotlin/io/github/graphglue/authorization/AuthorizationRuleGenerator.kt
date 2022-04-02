package io.github.graphglue.authorization

import io.github.graphglue.model.Rule
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Node

/**
 * Interface for beans which can create authorization conditions for rules
 */
fun interface AuthorizationRuleGenerator {

    /**
     * Generates a condition for a specific rule
     *
     * @param node the [Node] on which the condition should be applied
     * @param rule the [Rule] to generate the condition for
     * @param permission context for condition generation
     * @return the generated condition
     */
    fun generateCondition(node: Node, rule: Rule, permission: Permission): Condition
}
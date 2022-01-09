package de.graphglue.neo4j.authorization

import de.graphglue.model.Rule
import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Node

fun interface AuthorizationRuleGenerator {
    fun generateCondition(node: Node, rule: Rule, authorizationContext: AuthorizationContext): Condition
}
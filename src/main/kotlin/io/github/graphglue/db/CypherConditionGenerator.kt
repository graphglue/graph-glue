package io.github.graphglue.db

import org.neo4j.cypherdsl.core.Condition
import org.neo4j.cypherdsl.core.Node

/**
 * Used by classes which are able to generate conditions based on a  [Node]
 */
fun interface CypherConditionGenerator {
    /**
     * Creates the condition
     *
     * @param node the node for which the condition is generated
     * @return the generated [Condition]
     */
    fun generateCondition(node: Node): Condition
}
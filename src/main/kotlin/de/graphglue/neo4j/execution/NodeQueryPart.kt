package de.graphglue.neo4j.execution

import de.graphglue.neo4j.execution.definition.NodeDefinition

class NodeQueryPart(val subQueries: List<NodeSubQuery>) {
    private val subQueryLookup = subQueries.groupBy { it.resultKey }

    fun getSubQuery(resultKey: String, nodeDefinitionProvider: () -> NodeDefinition): NodeSubQuery {
        val subQueries = subQueryLookup[resultKey]!!
        return if (subQueries.size == 1) {
            subQueries.first()
        } else {
            val nodeDefinition = nodeDefinitionProvider()
            subQueries.first { it.onlyOnTypes.contains(nodeDefinition) }
        }
    }
}
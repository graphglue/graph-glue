package com.nkcoding.graphglue.neo4j.execution

import com.nkcoding.graphglue.graphql.connection.order.Order
import com.nkcoding.graphglue.graphql.connection.order.OrderDirection
import com.nkcoding.graphglue.graphql.execution.NodeQuery
import com.nkcoding.graphglue.graphql.execution.NodeSubQuery
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinition
import org.neo4j.cypherdsl.core.*
import org.neo4j.cypherdsl.core.renderer.Configuration
import org.neo4j.cypherdsl.core.renderer.Renderer
import org.springframework.data.neo4j.core.Neo4jClient
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext

const val NODE_KEY = "node"
const val NODES_KEY = "nodes"
const val TOTAL_COUNT_KEY = "total_count"

class NodeQueryExecutor(
    private val nodeQuery: NodeQuery, private val client: Neo4jClient, private val mappingContext: Neo4jMappingContext
) {
    private var nameCounter = 0

    fun parseQuery(): String {
        val (statement, _) = createRootNodeQuery()
        return Renderer.getRenderer(Configuration.prettyPrinting()).render(statement)
    }

    private fun createRootNodeQuery(): Pair<Statement, SymbolicName> {
        val rootNode = createNodeDefinitionNode(nodeQuery.definition).named(generateUniqueName().value)
        val builder = Cypher.match(rootNode).with(rootNode)
        return createNodeQuery(nodeQuery, builder, rootNode)
    }

    private fun createNodeQuery(
        nodeQuery: NodeQuery,
        builder: StatementBuilder.OrderableOngoingReadingAndWithWithoutWhere,
        node: Node
    ): Pair<Statement, SymbolicName> {
        val nodeDefinition = nodeQuery.definition
        val nodeAlias = node.symbolicName.orElseThrow()

        // filter
        val options = nodeQuery.options
        val filteredBuilder = if (options.filters.isEmpty()) {
            builder
        } else {
            val filter = options.filters.fold(Conditions.noCondition()) { condition, filter ->
                condition.and(filter.generateCondition(node))
            }
            builder.where(filter)
        }

        // calc totalCount
        val totalCount = generateUniqueName()
        val (totalCountBuilder, additionalWithExpressions) = if (options.fetchTotalCount) {
            val allNodesCollected = generateUniqueName()
            val newBuilder = filteredBuilder.with(Functions.collect(node).`as`(allNodesCollected))
                .with(Functions.size(allNodesCollected).`as`(totalCount), allNodesCollected as Expression)
                .unwind(allNodesCollected).`as`(nodeAlias)
            newBuilder to listOf(totalCount)
        } else {
            filteredBuilder to emptyList()
        }
        val allWithExpressions = listOf(nodeAlias) + additionalWithExpressions

        // filter for after and before
        val afterAndBeforeBuilder = if (options.after != null || options.before != null) {
            var filterCondition = Conditions.noCondition()
            if (options.after != null) {
                filterCondition = filterCondition.and(
                    generateCursorFilterExpression(
                        options.after,
                        options.orderBy,
                        nodeAlias,
                        true
                    )
                )
            }
            if (options.before != null) {
                filterCondition = filterCondition.and(
                    generateCursorFilterExpression(
                        options.before,
                        options.orderBy,
                        nodeAlias,
                        false
                    )
                )
            }
            totalCountBuilder.with(allWithExpressions)
                .where(filterCondition).with(allWithExpressions)
        } else {
            totalCountBuilder.with(allWithExpressions)
        }

        // limit the results
        val limitedBuilder = if (options.first != null) {
            afterAndBeforeBuilder.orderBy(generateOrderFields(options.orderBy, nodeAlias, false))
                .limit(options.first)
                .with(allWithExpressions)
        } else if (options.last != null) {
            afterAndBeforeBuilder.orderBy(generateOrderFields(options.orderBy, nodeAlias, true))
                .limit(options.last)
                .with(allWithExpressions)
        } else {
            afterAndBeforeBuilder
        }

        // perform subqueries
        var callBuilder: StatementBuilder.OngoingReading = limitedBuilder
        val subQueryResultList = ArrayList<SymbolicName>()
        for (part in nodeQuery.parts.values) {
            for (subQuery in part.subQueries) {
                val (subQueryStatement, resultName) = createSubQuery(subQuery, node)
                callBuilder = callBuilder.call(subQueryStatement)
                subQueryResultList.add(resultName)
                //TODO put result in map
            }
        }

        // build result map
        val resultNodeMap =
            mapOf(NODE_KEY to nodeDefinition.returnExpression) + subQueryResultList.associateBy { it.value }
        val resultNodeExpression = Cypher.asExpression(resultNodeMap)
        val resultNode = generateUniqueName()
        val resultBuilder =
            callBuilder.with(listOf(nodeAlias.`as`(nodeDefinition.returnNodeName)) + additionalWithExpressions)
                .with(listOf(resultNodeExpression.`as`(resultNode)) + allWithExpressions)

        // order and collect nodes
        val collectedNodes = generateUniqueName()
        val orderedCollectedResultsBuilder = resultBuilder.orderBy(generateOrderFields(options.orderBy, nodeAlias))
            .with(listOf(Functions.collect(resultNode).`as`(collectedNodes)) + additionalWithExpressions)


        // return
        val returnAlias = generateUniqueName()
        val returnBuilder = orderedCollectedResultsBuilder.returning(
            Cypher.asExpression(
                mapOf(
                    NODES_KEY to collectedNodes,
                    TOTAL_COUNT_KEY to if (options.fetchTotalCount) totalCount else Cypher.literalNull()
                )
            ).`as`(returnAlias)
        )
        return returnBuilder.build() to returnAlias
    }

    private fun generateCursorFilterExpression(
        cursor: Map<String, Any?>,
        order: Order<*>,
        node: SymbolicName,
        forwards: Boolean
    ): Condition {
        val realForwards = if (order.direction == OrderDirection.ASC) forwards else !forwards
        var filterExpression = Conditions.noCondition()
        for (part in order.field.parts.reversed()) {
            val property = node.property(part.neo4jPropertyName)
            val propertyValue = Cypher.anonParameter<Any?>(cursor[part.property.name])
            filterExpression = property.eq(propertyValue).and(filterExpression)
            val neqCondition = if (realForwards) {
                property.gt(propertyValue)
            } else {
                property.lt(propertyValue)
            }
            filterExpression = neqCondition.or(filterExpression)
        }
        return filterExpression
    }

    private fun generateOrderFields(order: Order<*>, node: SymbolicName, reversed: Boolean = false): List<SortItem> {
        val direction = if ((order.direction == OrderDirection.ASC) != reversed) {
            SortItem.Direction.ASC
        } else {
            SortItem.Direction.DESC
        }
        return order.field.parts.map { Cypher.sort(node.property(it.neo4jPropertyName), direction) }
    }

    private fun createSubQuery(subQuery: NodeSubQuery, node: Node): Pair<Statement, SymbolicName> {
        var labelCondition = Conditions.noCondition()
        for (nodeDefinition in subQuery.onlyOnTypes) {
            labelCondition = labelCondition.or(node.hasLabels(getNodeDefinitionPrimaryLabel(nodeDefinition)))
        }
        val nodeQuery = subQuery.query
        val relatedNode = createNodeDefinitionNode(nodeQuery.definition).named(generateUniqueName().value)

        val builder = Cypher.with(node)
            .with(node)
            .where(labelCondition)
            .match(subQuery.relationshipDefinition.generateRelationship(node, relatedNode))
            .with(relatedNode)
        return createNodeQuery(nodeQuery, builder, relatedNode)
    }

    private fun generateUniqueName() = Cypher.name("a_${nameCounter++}")

    private fun getNodeDefinitionPrimaryLabel(nodeDefinition: NodeDefinition): String {
        val persistentEntity = nodeDefinition.persistentEntity
        return persistentEntity.primaryLabel
    }

    private fun createNodeDefinitionNode(nodeDefinition: NodeDefinition): Node {
        return Cypher.node(getNodeDefinitionPrimaryLabel(nodeDefinition))
    }
}
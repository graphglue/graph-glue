package com.nkcoding.graphglue.neo4j.execution

import com.nkcoding.graphglue.graphql.connection.order.Order
import com.nkcoding.graphglue.graphql.connection.order.OrderDirection
import com.nkcoding.graphglue.graphql.execution.NodeQuery
import com.nkcoding.graphglue.graphql.execution.NodeSubQuery
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinition
import org.neo4j.cypherdsl.core.*
import org.springframework.data.neo4j.core.Neo4jClient
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import org.springframework.data.neo4j.core.mapping.Neo4jPersistentEntity

class NodeQueryExecutor(
    private val nodeQuery: NodeQuery, private val client: Neo4jClient, private val mappingContext: Neo4jMappingContext
) {
    private var nameCounter = 0

    init {
        createRootNodeQuery()
    }

    private fun createRootNodeQuery() {
        val rootNode = createNodeDefinitionNode(nodeQuery.definition).named(generateUniqueName().value)
        val builder = Cypher.match(rootNode).with(rootNode)
        createNodeQuery(nodeQuery, builder, rootNode)
    }

    private fun createNodeQuery(
        nodeQuery: NodeQuery,
        builder: StatementBuilder.OrderableOngoingReadingAndWithWithoutWhere,
        node: Node
    ): Pair<Statement, SymbolicName> {
        val nodeAlias = node.symbolicName.orElseThrow()

        // filter
        val options = nodeQuery.options
        val filter = options.filters.fold(Conditions.isTrue()) { condition, filter ->
            condition.and(filter.generateCondition(node))
        }
        val filteredBuilder = builder.where(filter)

        // calc totalCount
        val totalCount = generateUniqueName()
        val (totalCountBuilder, allWithExpressions) = if (options.fetchTotalCount) {
            val allNodesCollected = generateUniqueName()
            val newBuilder = filteredBuilder.with(Functions.collect(node).`as`(allNodesCollected))
                .with(Functions.size(allNodesCollected).`as`(totalCount), allNodesCollected as Expression)
                .unwind(allNodesCollected).`as`(nodeAlias).with(nodeAlias, totalCount)
            newBuilder to listOf(nodeAlias, totalCount)
        } else {
            filteredBuilder.with(nodeAlias) to listOf(nodeAlias)
        }

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
            totalCountBuilder.where(filterCondition).with(allWithExpressions)
        } else {
            totalCountBuilder
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
                callBuilder = callBuilder.call(subQueryStatement, nodeAlias)
                subQueryResultList.add(nodeAlias)
                //TODO put result in map
            }
        }

        // build result map

        // order and collect nodes

        // return
        val returnAlias = generateUniqueName()
        TODO()
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
            val propertyValue = Cypher.literalOf<Any?>(cursor[part.property.name])
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
            .where(labelCondition)
            .match(subQuery.relationshipDefinition.generateRelationship(node, relatedNode))
            .with(relatedNode)
        return createNodeQuery(nodeQuery, builder, relatedNode)
    }

    private fun generateUniqueName() = Cypher.name("a_${nameCounter++}")

    private fun nodeDefinitionToPersistentEntity(nodeDefinition: NodeDefinition): Neo4jPersistentEntity<*> {
        return mappingContext.getPersistentEntity(nodeDefinition.nodeType.java)!!
    }

    private fun getNodeDefinitionPrimaryLabel(nodeDefinition: NodeDefinition): String {
        val persistentEntity = nodeDefinitionToPersistentEntity(nodeDefinition)
        return persistentEntity.primaryLabel
    }

    private fun createNodeDefinitionNode(nodeDefinition: NodeDefinition): Node {
        return Cypher.node(getNodeDefinitionPrimaryLabel(nodeDefinition))
    }
}
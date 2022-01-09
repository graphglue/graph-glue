package de.graphglue.neo4j.execution

import de.graphglue.graphql.connection.order.Order
import de.graphglue.graphql.connection.order.OrderDirection
import de.graphglue.neo4j.execution.definition.NodeDefinition
import kotlinx.coroutines.reactor.awaitSingle
import org.neo4j.cypherdsl.core.*
import org.neo4j.cypherdsl.core.renderer.Configuration
import org.neo4j.cypherdsl.core.renderer.Renderer
import org.neo4j.driver.Value
import org.neo4j.driver.types.TypeSystem
import org.slf4j.LoggerFactory
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext

const val NODE_KEY = "node"
const val NODES_KEY = "nodes"
const val TOTAL_COUNT_KEY = "total_count"

class NodeQueryExecutor(
    private val nodeQuery: NodeQuery,
    private val client: ReactiveNeo4jClient,
    private val mappingContext: Neo4jMappingContext
) {
    private var nameCounter = 0
    private val subQueryLookup = HashMap<String, NodeSubQuery>()
    private val entityConverter = mappingContext.entityConverter

    private val logger = LoggerFactory.getLogger(NodeQueryExecutor::class.java)

    suspend fun execute(): NodeQueryResult<*> {
        val (statement, returnName) = createRootNodeQuery()
        logger.info(Renderer.getRenderer(Configuration.prettyPrinting()).render(statement))
        return client.query(Renderer.getDefaultRenderer().render(statement))
            .bindAll(statement.parameters)
            .fetchAs(NodeQueryResult::class.java)
            .mappedBy { typeSystem, record ->
                val result = record[returnName.value]
                parseQueryResult(typeSystem, result[NODES_KEY], result[TOTAL_COUNT_KEY], nodeQuery)
            }.one().awaitSingle()
    }

    private fun createRootNodeQuery(): StatementWithSymbolicName {
        val rootNode = createNodeDefinitionNode(nodeQuery.definition).named(generateUniqueName().value)
        val builder = Cypher.match(rootNode).with(rootNode)
        return createNodeQuery(nodeQuery, builder, rootNode)
    }

    private fun createNodeQuery(
        nodeQuery: NodeQuery,
        builder: StatementBuilder.OrderableOngoingReadingAndWithWithoutWhere,
        node: Node
    ): StatementWithSymbolicName {
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

        // collect to list
        val allNodesCollected = generateUniqueName()
        val collectedNodesBuilder = filteredBuilder.with(Functions.collect(node).`as`(allNodesCollected))

        // calc totalCount
        val totalCount = generateUniqueName()
        val totalCountBuilder = if (options.fetchTotalCount) {
            collectedNodesBuilder
                .with(Functions.size(allNodesCollected).`as`(totalCount), allNodesCollected as Expression)
        } else {
            collectedNodesBuilder
        }

        // perform main SubQuery
        val (mainSubQuery, nodesCollected) = generateMainSubQuery(
            nodeQuery,
            node,
            allNodesCollected
        )
        val mainSubQueryBuilder = totalCountBuilder.call(mainSubQuery)

        // return
        val returnAlias = generateUniqueName()
        val returnBuilder = mainSubQueryBuilder.returning(
            Cypher.asExpression(
                mapOf(
                    NODES_KEY to nodesCollected,
                    TOTAL_COUNT_KEY to if (options.fetchTotalCount) totalCount else Cypher.literalNull()
                )
            ).`as`(returnAlias)
        )
        return StatementWithSymbolicName(returnBuilder.build(), returnAlias)
    }

    private fun generateMainSubQuery(
        nodeQuery: NodeQuery,
        node: Node,
        allNodesCollected: SymbolicName
    ): StatementWithSymbolicName {
        val options = nodeQuery.options
        val nodeAlias = node.requiredSymbolicName
        val nodeDefinition = nodeQuery.definition

        // create subquery
        val subQueryBuilder = Cypher.with(allNodesCollected)
            .unwind(allNodesCollected).`as`(nodeAlias)

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
            subQueryBuilder.with(nodeAlias)
                .where(filterCondition).with(nodeAlias)
        } else {
            subQueryBuilder.with(nodeAlias)
        }

        // limit the results
        val limitedBuilder = if (options.first != null) {
            afterAndBeforeBuilder.orderBy(generateOrderFields(options.orderBy, nodeAlias, false))
                .limit(options.first)
                .with(nodeAlias)
        } else if (options.last != null) {
            afterAndBeforeBuilder.orderBy(generateOrderFields(options.orderBy, nodeAlias, true))
                .limit(options.last)
                .with(nodeAlias)
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
                subQueryLookup[resultName.value!!] = subQuery
            }
        }

        // build result map
        val resultNodeMap =
            mapOf(NODE_KEY to Cypher.listOf(nodeDefinition.returnExpression)) + subQueryResultList.associateBy { it.value }
        val resultNodeExpression = Cypher.asExpression(resultNodeMap)
        val resultNode = generateUniqueName()
        val resultBuilder =
            callBuilder.with(
                listOf(
                    nodeAlias.`as`(nodeDefinition.returnNodeName),
                    nodeAlias
                ) + subQueryResultList
            )
                .with(listOf(resultNodeExpression.`as`(resultNode)) + nodeAlias)

        // order and collect nodes
        val collectedNodes = generateUniqueName()
        val statement =
            resultBuilder.orderBy(generateOrderFields(options.orderBy, nodeAlias))
                .with(listOf(Functions.collect(resultNode).`as`(collectedNodes))).returning(collectedNodes).build()
        return StatementWithSymbolicName(statement, collectedNodes)
    }

    private fun generateCursorFilterExpression(
        cursor: Map<String, Any?>,
        order: Order<*>,
        node: SymbolicName,
        forwards: Boolean
    ): Condition {
        val realForwards = if (order.direction == OrderDirection.ASC) forwards else !forwards
        return order.field.parts.asReversed().foldIndexed(Conditions.noCondition()) { index, filterExpression, part ->
            var newFilterExpression = filterExpression
            val property = node.property(part.neo4jPropertyName)
            val propertyValue = Cypher.anonParameter<Any?>(cursor[part.property.name])
            if (index > 0) {
                newFilterExpression = property.eq(propertyValue).and(newFilterExpression)
            }
            val neqCondition = if (realForwards) {
                property.gt(propertyValue)
            } else {
                property.lt(propertyValue)
            }
            neqCondition.or(newFilterExpression)
        }
    }

    private fun generateOrderFields(order: Order<*>, node: SymbolicName, reversed: Boolean = false): List<SortItem> {
        val direction = if ((order.direction == OrderDirection.ASC) != reversed) {
            SortItem.Direction.ASC
        } else {
            SortItem.Direction.DESC
        }
        return order.field.parts.map { Cypher.sort(node.property(it.neo4jPropertyName), direction) }
    }

    private fun createSubQuery(subQuery: NodeSubQuery, node: Node): StatementWithSymbolicName {
        var labelCondition = Conditions.noCondition()
        for (nodeDefinition in subQuery.onlyOnTypes) {
            labelCondition = labelCondition.or(node.hasLabels(nodeDefinition.primaryLabel))
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

    private fun createNodeDefinitionNode(nodeDefinition: NodeDefinition): Node {
        return Cypher.node(nodeDefinition.primaryLabel)
    }

    private fun parseQueryResult(
        typeSystem: TypeSystem,
        nodesValue: Value,
        totalCountValue: Value,
        nodeQuery: NodeQuery
    ): NodeQueryResult<*> {
        val nodes = nodesValue.asList { parseNode(typeSystem, it, nodeQuery.definition) }
        val totalCount = if (totalCountValue.isNull) null else totalCountValue.asInt()
        return NodeQueryResult(nodeQuery.options, nodes, totalCount)
    }

    private fun parseNode(
        typeSystem: TypeSystem,
        value: Value,
        nodeDefinition: NodeDefinition
    ): de.graphglue.model.Node {
        val node = entityConverter.read(nodeDefinition.nodeType.java, value.get(NODE_KEY))
        for (relatedNodeName in value.keys()) {
            if (relatedNodeName != NODE_KEY) {
                val relatedNodesValue = value[relatedNodeName]
                val subQuery = subQueryLookup[relatedNodeName]!!
                val subQueryResult = parseQueryResult(
                    typeSystem,
                    relatedNodesValue[NODES_KEY],
                    relatedNodesValue[TOTAL_COUNT_KEY],
                    subQuery.query
                )
                val relationshipDefinition = subQuery.relationshipDefinition
                @Suppress("UNCHECKED_CAST")
                relationshipDefinition.registerQueryResult(
                    node,
                    subQueryResult as NodeQueryResult<de.graphglue.model.Node>
                )
            }
        }
        return node
    }
}

private data class StatementWithSymbolicName(val statement: Statement, val symbolicName: SymbolicName)
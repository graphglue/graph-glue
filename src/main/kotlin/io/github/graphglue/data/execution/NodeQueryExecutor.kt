package io.github.graphglue.data.execution

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.connection.order.Order
import io.github.graphglue.connection.order.OrderDirection
import kotlinx.coroutines.reactor.awaitSingle
import org.neo4j.cypherdsl.core.*
import org.neo4j.cypherdsl.core.renderer.Renderer
import org.neo4j.driver.Value
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext

/**
 * Name for the single node map entry
 */
const val NODE_KEY = "node"

/**
 * Name for the node list map entry
 */
const val NODES_KEY = "nodes"

/**
 * Name for the total count map entry
 */
const val TOTAL_COUNT_KEY = "total_count"

/**
 * Generates Cypher-DSL queries based on a [NodeQuery], executes it and parses the result
 *
 * @param nodeQuery the query to execute
 * @param client used to execute the query
 * @param mappingContext used to transform the result into a node
 */
class NodeQueryExecutor(
    private val nodeQuery: NodeQuery,
    private val client: ReactiveNeo4jClient,
    private val mappingContext: Neo4jMappingContext
) {
    /**
     * Counter for unique name generation
     */
    private var nameCounter = 0

    /**
     * lookup for generated subqueries
     */
    private val subQueryLookup = HashMap<String, NodeSubQuery>()

    /**
     * Executes the query
     *
     * @return the query result including all found nodes
     */
    suspend fun execute(): NodeQueryResult<*> {
        val (statement, returnName) = createRootNodeQuery()
        return client.query(Renderer.getDefaultRenderer().render(statement))
            .bindAll(statement.parameters)
            .fetchAs(NodeQueryResult::class.java)
            .mappedBy { _, record ->
                val result = record[returnName.value]
                parseQueryResult(result[NODES_KEY], result[TOTAL_COUNT_KEY], nodeQuery)
            }.one().awaitSingle()
    }

    /**
     * Generates the query for [nodeQuery]
     *
     * @return the generated query and result column name
     */
    private fun createRootNodeQuery(): StatementWithSymbolicName {
        val rootNode = createNodeDefinitionNode(nodeQuery.definition).named(generateUniqueName().value)
        val builder = Cypher.match(rootNode).with(rootNode)
        return createNodeQuery(nodeQuery, builder, rootNode)
    }

    /**
     * Generates a query based on `nodeQuery`
     *
     * @param nodeQuery defines the query to convert
     * @param builder the start of the query, used to generate the full query
     * @param node associated node for conditions and relations
     * @return the generated query and result column name
     */
    private fun createNodeQuery(
        nodeQuery: NodeQuery,
        builder: StatementBuilder.OrderableOngoingReadingAndWithWithoutWhere,
        node: Node
    ): StatementWithSymbolicName {
        val options = nodeQuery.options
        val filteredBuilder = applyFilterConditions(options, builder, node)
        val allNodesCollected = generateUniqueName()
        val collectedNodesBuilder = filteredBuilder.with(Functions.collect(node).`as`(allNodesCollected))
        val totalCount = generateUniqueName()
        val totalCountBuilder = applyTotalCount(options, collectedNodesBuilder, allNodesCollected, totalCount)
        val (mainSubQuery, nodesCollected) = generateMainSubQuery(nodeQuery, node, allNodesCollected)
        val mainSubQueryBuilder = totalCountBuilder.call(mainSubQuery)
        return createReturnStatement(mainSubQueryBuilder, nodesCollected, options, totalCount)
    }

    /**
     * Applies all filter conditions to a builder and returns the resulting builder
     *
     * @param options options which define filters
     * @param builder builder for  Cypher-DSL query
     * @param node the node to generate the filter conditions for
     * @return the resulting builder
     */
    private fun applyFilterConditions(
        options: NodeQueryOptions,
        builder: StatementBuilder.OrderableOngoingReadingAndWithWithoutWhere,
        node: Node
    ) = if (options.filters.isEmpty()) {
        builder
    } else {
        val filter = options.filters.fold(Conditions.noCondition()) { condition, filter ->
            condition.and(filter.generateCondition(node))
        }
        builder.where(filter)
    }

    /**
     * If necessary, adds a with statement to the `builder` which fetches totalCount
     *
     * @param options options of the query, defines if totalCount must be fetched
     * @param builder ongoing statement builder
     * @param allNodesCollected name for the variable containing a collection of all nodes
     * @param totalCount name for the variable under which totalCount should be saved
     * @return the new builder
     */
    private fun applyTotalCount(
        options: NodeQueryOptions,
        builder: StatementBuilder.OrderableOngoingReadingAndWithWithoutWhere,
        allNodesCollected: SymbolicName,
        totalCount: SymbolicName
    ) = if (options.fetchTotalCount) {
        builder
            .with(Functions.size(allNodesCollected).`as`(totalCount), allNodesCollected as Expression)
    } else {
        builder
    }

    /**
     * Creates the return statement and builds the query.
     * If totalCount is not fetched, `null` is set as value for totalCount
     *
     * @param builder ongoing statement builder
     * @param nodesCollected name for the variable containing a collection of all nodes
     * @param options options of the query, defines if totalCount must be fetched
     * @param totalCount name for the variable under which totalCount should be saved
     * @return the generated query and result column name
     */
    private fun createReturnStatement(
        builder: StatementBuilder.OngoingReadingWithoutWhere,
        nodesCollected: SymbolicName,
        options: NodeQueryOptions,
        totalCount: SymbolicName
    ): StatementWithSymbolicName {
        val returnAlias = generateUniqueName()
        val returnBuilder = builder.returning(
            Cypher.asExpression(
                mapOf(
                    NODES_KEY to nodesCollected,
                    TOTAL_COUNT_KEY to if (options.fetchTotalCount) totalCount else Cypher.literalNull()
                )
            ).`as`(returnAlias)
        )
        return StatementWithSymbolicName(returnBuilder.build(), returnAlias)
    }

    /**
     * Generates a Cypher subquery which gets the list of all nodes,
     * and applies pagination filtering, related nodes subqueries, ordering and result aggregation
     *
     * @param nodeQuery the currently converted query
     * @param node node which shall be used when unwinding `allNodesCollected`
     * @param allNodesCollected name of the input variable containing a collection of all nodes
     * @return the generated subquery and name of the result row
     */
    private fun generateMainSubQuery(
        nodeQuery: NodeQuery,
        node: Node,
        allNodesCollected: SymbolicName
    ): StatementWithSymbolicName {
        val options = nodeQuery.options
        val nodeAlias = node.requiredSymbolicName
        val nodeDefinition = nodeQuery.definition
        val subQueryBuilder = Cypher.with(allNodesCollected).unwind(allNodesCollected).`as`(nodeAlias)
        val afterAndBeforeBuilder = applyAfterAndBefore(options, nodeAlias, subQueryBuilder)
        val limitedBuilder = applyResultLimiters(options, afterAndBeforeBuilder, nodeAlias)
        val (callBuilder, subQueryResultList) = applyPartsSubqueries(limitedBuilder, node, nodeQuery)
        return generateMainSubQueryResultStatement(nodeDefinition, subQueryResultList, callBuilder, nodeAlias, options)
    }

    /**
     * Generates the result statement for the query generated by [generateMainSubQuery].
     * Also orders the nodes, and builds a statement out of the builder
     *
     * @param nodeDefinition definition for the currently queried node
     * @param subQueryResultList list of the result variable names of subqueries that should be present in the result
     * @param builder ongoing statement builder
     * @param nodeAlias name of the variable containing the node
     * @param options options for the query, defines order
     * @return the generated statement and result column name
     */
    private fun generateMainSubQueryResultStatement(
        nodeDefinition: NodeDefinition,
        subQueryResultList: List<SymbolicName>,
        builder: StatementBuilder.OngoingReading,
        nodeAlias: SymbolicName,
        options: NodeQueryOptions
    ): StatementWithSymbolicName {
        val resultNodeMap = mapOf(NODE_KEY to Cypher.listOf(nodeDefinition.returnExpression))
            .plus(subQueryResultList.associateBy { it.value })
        val resultNodeExpression = Cypher.asExpression(resultNodeMap)
        val resultNode = generateUniqueName()
        val resultBuilder = builder.with(
            listOf(
                nodeAlias.`as`(nodeDefinition.returnNodeName),
                nodeAlias
            ) + subQueryResultList
        ).with(listOf(resultNodeExpression.`as`(resultNode)) + nodeAlias)
        val collectedNodes = generateUniqueName()
        val statement = resultBuilder.orderBy(generateOrderFields(options.orderBy, nodeAlias))
            .with(listOf(Functions.collect(resultNode).`as`(collectedNodes))).returning(collectedNodes).build()
        return StatementWithSymbolicName(statement, collectedNodes)
    }

    /**
     * Adds before and after filters to the `builder`
     *
     * @param options defines after and before
     * @param nodeAlias name of the variable containing the node
     * @param builder ongoing statement builder
     * @return new builder with after and before applied
     */
    private fun applyAfterAndBefore(
        options: NodeQueryOptions,
        nodeAlias: SymbolicName,
        builder: StatementBuilder.OngoingReading
    ) = if (options.after != null || options.before != null) {
        var filterCondition = Conditions.noCondition()
        if (options.after != null) {
            filterCondition = filterCondition.and(
                generateCursorFilterExpression(options.after, options.orderBy, nodeAlias, true)
            )
        }
        if (options.before != null) {
            filterCondition = filterCondition.and(
                generateCursorFilterExpression(options.before, options.orderBy, nodeAlias, false)
            )
        }
        builder.with(nodeAlias)
            .where(filterCondition).with(nodeAlias)
    } else {
        builder.with(nodeAlias)
    }

    /**
     * Orders the nodes and adds  first and last filters
     *
     * @param options defines first and last
     * @param builder ongoing statement builder
     * @param nodeAlias name of the variable containing the node
     * @return the builder with first/last applied
     */
    private fun applyResultLimiters(
        options: NodeQueryOptions,
        builder: StatementBuilder.OrderableOngoingReadingAndWithWithoutWhere,
        nodeAlias: SymbolicName
    ) = if (options.first != null) {
        builder.orderBy(generateOrderFields(options.orderBy, nodeAlias, false))
            .limit(options.first)
            .with(nodeAlias)
    } else if (options.last != null) {
        builder.orderBy(generateOrderFields(options.orderBy, nodeAlias, true))
            .limit(options.last)
            .with(nodeAlias)
    } else {
        builder
    }

    /**
     * Generates subqueries for all subqueries defined in parts and adds them to the statement builder
     *
     * @param builder ongoing statement builder
     * @param node the current node, necessary to build relation condition for subquery
     * @param nodeQuery the query for which all subqueries should be applied
     * @return the new builder and a list of the names of all subquery result variables
     */
    private fun applyPartsSubqueries(
        builder: StatementBuilder.OngoingReading,
        node: Node,
        nodeQuery: NodeQuery
    ): Pair<StatementBuilder.OngoingReading, List<SymbolicName>> {
        var callBuilder = builder
        val subQueryResultList = ArrayList<SymbolicName>()
        for (part in nodeQuery.parts.values) {
            for (subQuery in part.subQueries) {
                val (subQueryStatement, resultName) = createSubQuery(subQuery, node)
                callBuilder = callBuilder.call(subQueryStatement)
                subQueryResultList.add(resultName)
                subQueryLookup[resultName.value!!] = subQuery
            }
        }
        return Pair(callBuilder, subQueryResultList)
    }

    /**
     * Generates a [Condition] which can be used to filter for items before/after a specific cursor
     *
     * @param cursor the parsed cursor
     * @param order order in which the nodes will be sorted, necessary to interpret cursor
     * @param node the name of the variable storing the node to filter
     * @param forwards if `true`, filters for items after the cursor, otherwise for items before the cursor
     *                 (both inclusive)
     * @return an [Expression] which can be used to filter for items after/before the provided `cursor`
     */
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

    /**
     * Transforms an [Order] into  a list of [SortItem]
     *
     * @param order the [Order] to  transform
     * @param node name of the variable containing the node
     * @param reversed if `true`, the direction defined by `order` is reversed
     * @return the list of generated [SortItem]
     */
    private fun generateOrderFields(order: Order<*>, node: SymbolicName, reversed: Boolean = false): List<SortItem> {
        val direction = if ((order.direction == OrderDirection.ASC) != reversed) {
            SortItem.Direction.ASC
        } else {
            SortItem.Direction.DESC
        }
        return order.field.parts.map { Cypher.sort(node.property(it.neo4jPropertyName), direction) }
    }

    /**
     * Creates a subquery using a Cypher subquery
     * Uses [NodeSubQuery.onlyOnTypes] to only fetch related nodes when necessary
     *
     * @param subQuery the subquery to convert
     * @param node parent side defining node of the relation
     * @return the generated statement and result column name
     */
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

    /**
     * Generates a new unique name
     */
    private fun generateUniqueName() = Cypher.name("a_${nameCounter++}")

    /**
     * Creates a [Node] for a [NodeDefinition].
     *
     * @param nodeDefinition the definition for which to create the [Node]
     * @return the generated [Node] with the correct primaryLabel
     */
    private fun createNodeDefinitionNode(nodeDefinition: NodeDefinition): Node {
        return Cypher.node(nodeDefinition.primaryLabel)
    }

    /**
     * Parses the result of a query (a list of nodes, and optional a totalCount).
     * Parses recursively
     *
     * @param nodesValue the returned node list value
     * @param totalCountValue the returned totalCount value
     * @param nodeQuery the query used to get the results
     * @return the generated result
     */
    private fun parseQueryResult(
        nodesValue: Value,
        totalCountValue: Value,
        nodeQuery: NodeQuery
    ): NodeQueryResult<*> {
        val nodes = nodesValue.asList { parseNode(it, nodeQuery.definition) }
        val totalCount = if (totalCountValue.isNull) null else totalCountValue.asInt()
        return NodeQueryResult(nodeQuery.options, nodes, totalCount)
    }

    /**
     * Parses a single node
     * Parses subqueries recursively
     *
     * @param value the returned value for the node
     * @param nodeDefinition defines the type of node to convert
     * @return the parsed node
     */
    private fun parseNode(
        value: Value,
        nodeDefinition: NodeDefinition
    ): io.github.graphglue.model.Node {
        val node = mappingContext.entityConverter.read(nodeDefinition.nodeType.java, value.get(NODE_KEY))
        for (relatedNodeName in value.keys()) {
            if (relatedNodeName != NODE_KEY) {
                val relatedNodesValue = value[relatedNodeName]
                val subQuery = subQueryLookup[relatedNodeName]!!
                val subQueryResult = parseQueryResult(
                    relatedNodesValue[NODES_KEY],
                    relatedNodesValue[TOTAL_COUNT_KEY],
                    subQuery.query
                )
                val relationshipDefinition = subQuery.relationshipDefinition
                @Suppress("UNCHECKED_CAST")
                relationshipDefinition.registerQueryResult(
                    node,
                    subQueryResult as NodeQueryResult<io.github.graphglue.model.Node>
                )
            }
        }
        return node
    }
}

/**
 * Wrapper for a [Statement] and an associated name
 *
 * @param statement the Cypher-DSL statement of the query
 * @param symbolicName the result column name
 */
private data class StatementWithSymbolicName(val statement: Statement, val symbolicName: SymbolicName)
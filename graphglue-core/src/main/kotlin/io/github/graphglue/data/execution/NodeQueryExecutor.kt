package io.github.graphglue.data.execution

import io.github.graphglue.connection.order.Order
import io.github.graphglue.connection.order.OrderDirection
import io.github.graphglue.definition.NodeDefinition
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
 * Name for the parent node id map entry
 */
const val PARENT_NODE_ID_KEY = "parentId"

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
     * lookup for generated SubQueries
     */
    private val subQueryLookup = HashMap<NodeSubQuery, String>()

    /**
     * Lookup table for already created nodes
     */
    private val nodeLookup = HashMap<String, io.github.graphglue.model.Node>()

    /**
     * Executes the query
     *
     * @return the query result including all found nodes
     */
    suspend fun execute(): NodeQueryResult<*> {
        val (statement, returnName) = createRootNodeQuery()
        return client.query(Renderer.getDefaultRenderer().render(statement)).bindAll(statement.parameters)
            .fetchAs(NodeQueryResult::class.java).mappedBy { _, record ->
                parseQueryResultInternal(record, returnName, nodeQuery)
            }.one().awaitSingle()
    }

    /**
     * Generates a query based on `nodeQuery`
     * Must only be used for the root query
     *
     * @return the generated query and result column name
     */
    private fun createRootNodeQuery(): StatementWithSymbolicName {
        val node = nodeQuery.definition.node().named(generateUniqueName().value)
        val builder = Cypher.match(node).with(node)
        val options = nodeQuery.options
        val filteredBuilder = applyFilterConditions(options, builder, node)
        val allNodesCollected = generateUniqueName()
        val collectedNodesBuilder = filteredBuilder.with(Functions.collect(node).`as`(allNodesCollected))
        val totalCount = generateUniqueName()
        val totalCountBuilder =
            applyTotalCount(options, collectedNodesBuilder, allNodesCollected, emptyList(), totalCount)
        val (mainSubQuery, resultNodes, nodes) = generateMainSubQuery(nodeQuery, node, allNodesCollected)
        val mainSubQueryBuilder = totalCountBuilder.call(mainSubQuery)
        val (subPartsBuilder, returnNames) = createPartsSubQueriesRecursive(mainSubQueryBuilder, nodeQuery, nodes, 1)
        val (statement, returnName) = createRootReturnStatement(
            subPartsBuilder, resultNodes, returnNames, options, totalCount
        )
        return StatementWithSymbolicName(statement, returnName)
    }

    /**
     * Creates SubQueries for each SubQuery in each part of the provided [nodeQuery]
     * Generates recursively depth first.
     *
     * @param builder the builder to add the SubQuery calls to
     * @param nodeQuery contains the parts with the SubQueries
     * @param allNodes name of the collection containing all nodes, should be a nested collection of depth [unwindCount]
     * @param unwindCount nesting depth of [allNodes] collection
     * @return the new statement builder, and a list of all names to return (names of the SubQuery results)
     */
    private fun createPartsSubQueriesRecursive(
        builder: StatementBuilder.OngoingReadingWithoutWhere,
        nodeQuery: NodeQuery,
        allNodes: SymbolicName,
        unwindCount: Int
    ): Pair<StatementBuilder.OngoingReadingWithoutWhere, List<SymbolicName>> {
        var callBuilder = builder
        val returnNames = mutableListOf<SymbolicName>()
        for (part in nodeQuery.parts.values) {
            for (subQuery in part.subQueries) {
                val parentDefinition = nodeQuery.definition
                val (statement, resultNodes, nodes) = createSubQuery(subQuery, parentDefinition, allNodes, unwindCount)
                returnNames += resultNodes
                callBuilder = callBuilder.call(statement)
                subQueryLookup[subQuery] = resultNodes.value!!
                val (newBuilder, newReturnNames) = createPartsSubQueriesRecursive(callBuilder, subQuery.query, nodes, 2)
                callBuilder = newBuilder
                returnNames += newReturnNames
            }
        }
        return callBuilder to returnNames
    }

    /**
     * Creates the return statement and builds the query.
     * If totalCount is not fetched, `null` is set as value for totalCount
     * Must only be used for the root query
     *
     * @param builder ongoing statement builder
     * @param resultNodesCollected name for the variable containing a collection of all result nodes
     * @param otherResults names of other results which should be returned, e.g. the names of the SubQuery results
     * @param options options of the query, defines if totalCount must be fetched
     * @param totalCount name for the variable under which totalCount should be saved
     * @return the generated statement, the result name and the name of a collection with all raw nodes
     */
    private fun createRootReturnStatement(
        builder: StatementBuilder.OngoingReadingWithoutWhere,
        resultNodesCollected: SymbolicName,
        otherResults: List<SymbolicName>,
        options: NodeQueryOptions,
        totalCount: SymbolicName,
    ): StatementWithSymbolicName {
        val returnAlias = generateUniqueName()
        val returnBuilder = builder.returning(
            listOf(
                Cypher.asExpression(
                    mapOf(
                        NODES_KEY to resultNodesCollected,
                        TOTAL_COUNT_KEY to if (options.fetchTotalCount) totalCount else Cypher.literalNull()
                    )
                ).`as`(returnAlias)
            ) + otherResults
        )
        return StatementWithSymbolicName(returnBuilder.build(), returnAlias)
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
        options: NodeQueryOptions, builder: StatementBuilder.OrderableOngoingReadingAndWithWithoutWhere, node: Node
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
     * @param additionalNames additional names which should be added to the with statement
     * @param totalCount name for the variable under which totalCount should be saved
     * @return the new builder
     */
    private fun applyTotalCount(
        options: NodeQueryOptions,
        builder: StatementBuilder.OrderableOngoingReadingAndWithWithoutWhere,
        allNodesCollected: SymbolicName,
        additionalNames: List<SymbolicName>,
        totalCount: SymbolicName
    ) = if (options.fetchTotalCount) {
        builder.with(
            listOf(
                Functions.size(allNodesCollected).`as`(totalCount), allNodesCollected as Expression
            ) + additionalNames
        )
    } else {
        builder
    }

    /**
     * Generates a Cypher SubQuery which gets the list of all nodes,
     * and applies pagination filtering, related nodes SubQueries, ordering and result aggregation
     *
     * @param nodeQuery the currently converted query
     * @param node node which shall be used when unwinding `allNodesCollected`
     * @param allNodesCollected name of the input variable containing a collection of all nodes
     * @return the generated statement, the result name and the name of a collection with all raw nodes
     */
    private fun generateMainSubQuery(
        nodeQuery: NodeQuery, node: Node, allNodesCollected: SymbolicName
    ): StatementWithResultNodesAndNodes {
        val options = nodeQuery.options
        val nodeAlias = node.requiredSymbolicName
        val nodeDefinition = nodeQuery.definition
        val subQueryBuilder = Cypher.with(allNodesCollected).unwind(allNodesCollected).`as`(nodeAlias)
        val afterAndBeforeBuilder = applyAfterAndBefore(options, nodeAlias, subQueryBuilder)
        val limitedBuilder = applyResultLimiters(options, afterAndBeforeBuilder, nodeAlias)
        return generateMainSubQueryResultStatement(
            nodeDefinition, limitedBuilder, nodeAlias, options
        )
    }

    /**
     * Generates the result statement for the query generated by [generateMainSubQuery].
     * Also orders the nodes, and builds a statement out of the builder
     *
     * @param nodeDefinition definition for the currently queried node
     * @param builder ongoing statement builder
     * @param nodeAlias name of the variable containing the node
     * @param options options for the query, defines order
     * @return the generated statement, the result name and the name of a collection with all raw nodes
     */
    private fun generateMainSubQueryResultStatement(
        nodeDefinition: NodeDefinition,
        builder: StatementBuilder.OngoingReading,
        nodeAlias: SymbolicName,
        options: NodeQueryOptions
    ): StatementWithResultNodesAndNodes {
        val resultNodeMap = mapOf(NODE_KEY to Cypher.listOf(nodeDefinition.returnExpression))
        val resultNodeExpression = Cypher.asExpression(resultNodeMap)
        val resultNode = generateUniqueName()
        val resultBuilder = builder.with(
            listOf(
                nodeAlias.`as`(nodeDefinition.returnNodeName), nodeAlias
            )
        ).with(listOf(resultNodeExpression.`as`(resultNode)) + nodeAlias)
        val collectedResultNodes = generateUniqueName()
        val collectedNodes = generateUniqueName()
        val statement = resultBuilder.orderBy(generateOrderFields(options.orderBy, nodeAlias)).with(
            Functions.collect(resultNode).`as`(collectedResultNodes), Functions.collect(nodeAlias).`as`(collectedNodes)
        ).returning(collectedResultNodes, collectedNodes).build()
        return StatementWithResultNodesAndNodes(statement, collectedResultNodes, collectedNodes)
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
        options: NodeQueryOptions, nodeAlias: SymbolicName, builder: StatementBuilder.OngoingReading
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
        builder.with(nodeAlias).where(filterCondition).with(nodeAlias)
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
        builder.orderBy(generateOrderFields(options.orderBy, nodeAlias, false)).limit(options.first).with(nodeAlias)
    } else if (options.last != null) {
        builder.orderBy(generateOrderFields(options.orderBy, nodeAlias, true)).limit(options.last).with(nodeAlias)
    } else {
        builder
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
        cursor: Map<String, Any?>, order: Order<*>, node: SymbolicName, forwards: Boolean
    ): Condition {
        val realForwards = if (order.direction == OrderDirection.ASC) forwards else !forwards
        return order.field.parts.asReversed().foldIndexed(Conditions.noCondition()) { index, filterExpression, part ->
            var newFilterExpression = filterExpression
            val property = node.property(part.neo4jPropertyName)
            val value = cursor[part.name]
            val propertyValue = Cypher.anonParameter<Any?>(value)
            if (index > 0) {
                val eqCondition = if (value != null) property.eq(propertyValue) else property.isNull
                newFilterExpression = eqCondition.and(newFilterExpression)
            }
            val neqCondition = if (value == null) {
                property.isNotNull
            } else {
                if (realForwards) property.gt(propertyValue) else property.lt(propertyValue)
            }
            if (!part.isNullable || !realForwards) {
                neqCondition.or(newFilterExpression)
            } else if (value == null) {
                newFilterExpression
            } else {
                neqCondition.or(property.isNull).or(newFilterExpression)
            }
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
     * Creates a SubQuery using a Cypher SubQuery
     * Uses [NodeSubQuery.onlyOnTypes] to only fetch related nodes when necessary
     *
     * @param subQuery the SubQuery to convert
     * @param parentNodeDefinition the definition of the nodes in [allNodes]
     * @param allNodes the name of the collection containing all nodes
     * @param unwindCount the nesting depth of [allNodes]
     * @return the generated statement, the result name and the name of a collection with all raw nodes
     */
    private fun createSubQuery(
        subQuery: NodeSubQuery, parentNodeDefinition: NodeDefinition, allNodes: SymbolicName, unwindCount: Int
    ): StatementWithResultNodesAndNodes {
        val (builder, node) = applySubQueryUnwind(allNodes, unwindCount, parentNodeDefinition)
        var labelCondition = Conditions.noCondition()
        for (nodeDefinition in subQuery.onlyOnTypes) {
            labelCondition = labelCondition.or(node.hasLabels(nodeDefinition.primaryLabel))
        }
        val nodeQuery = subQuery.query
        val relatedNode = nodeQuery.definition.node().named(generateUniqueName().value)
        val innerBuilder = Cypher.with(node).with(node).where(labelCondition)
            .match(subQuery.relationshipDefinition.generateRelationship(node, relatedNode)).with(node, relatedNode)
        val (innerStatement, innerResultNodes, innerNodes) = createSubNodeQuery(
            nodeQuery, innerBuilder, relatedNode, node
        )
        val resultNodes = generateUniqueName()
        val nodes = generateUniqueName()
        return StatementWithResultNodesAndNodes(
            builder.with(node).call(innerStatement).returning(
                Functions.collect(innerResultNodes).`as`(resultNodes), Functions.collect(innerNodes).`as`(nodes)
            ).build(), resultNodes, nodes
        )
    }

    /**
     * Starts a new SubQuery builder and applies the specified amount of unwinds to [allNodes]
     *
     * @param allNodes the name of the collection containing all nodes
     * @param unwindCount the nesting depth of [allNodes]
     * @param parentNodeDefinition the definition of the nodes in [allNodes]
     * @return the generated statement builder, and the node representing the unwound [allNodes] collection
     */
    private fun applySubQueryUnwind(
        allNodes: SymbolicName,
        unwindCount: Int,
        parentNodeDefinition: NodeDefinition
    ): Pair<StatementBuilder.OngoingReading, Node> {
        var builder: StatementBuilder.OngoingReading = Cypher.with(allNodes)
        var toUnwind = allNodes
        repeat(unwindCount) {
            val newName = generateUniqueName()
            builder = builder.unwind(toUnwind).`as`(newName)
            toUnwind = newName
        }
        val node = parentNodeDefinition.node().named(toUnwind)
        return Pair(builder, node)
    }

    /**
     * Generates a query based on `nodeQuery`
     * Must only be used for SubQueries and not for the root query
     *
     * @param nodeQuery defines the query to convert
     * @param builder the start of the query, used to generate the full query
     * @param node associated node for conditions and relations
     * @param parentNode the [Node] [node] is related to
     * @return the generated query, the result name and the name of a collection with all raw nodes
     */
    private fun createSubNodeQuery(
        nodeQuery: NodeQuery,
        builder: StatementBuilder.OrderableOngoingReadingAndWithWithoutWhere,
        node: Node,
        parentNode: Node
    ): StatementWithResultNodesAndNodes {
        val options = nodeQuery.options
        val filteredBuilder = applyFilterConditions(options, builder, node)
        val allNodesCollected = generateUniqueName()
        val collectedNodesBuilder = filteredBuilder.with(Functions.collect(node).`as`(allNodesCollected), parentNode)
        val totalCount = generateUniqueName()
        val totalCountBuilder = applyTotalCount(
            options, collectedNodesBuilder, allNodesCollected, listOf(parentNode.requiredSymbolicName), totalCount
        )
        val (mainSubQuery, resultNodes, nodes) = generateMainSubQuery(nodeQuery, node, allNodesCollected)
        val mainSubQueryBuilder = totalCountBuilder.call(mainSubQuery)
        val parentId = parentNode.property("id")
        return createSubReturnStatement(mainSubQueryBuilder, resultNodes, nodes, parentId, options, totalCount)
    }

    /**
     * Creates the return statement and builds the query.
     * If totalCount is not fetched, `null` is set as value for totalCount
     * Must only be used for SubQueries and not for the root query
     *
     * @param builder ongoing statement builder
     * @param resultNodesCollected name for the variable containing a collection of all result nodes
     * @param nodesCollected name for the variable containing all raw nodes, used for parts SubQueries
     * @param parentNodeId statement for parent node id, set to `Cypher.literalNull()` if null
     * @param options options of the query, defines if totalCount must be fetched
     * @param totalCount name for the variable under which totalCount should be saved
     * @return the generated statement, the result name and the name of a collection with all raw nodes
     */
    private fun createSubReturnStatement(
        builder: StatementBuilder.OngoingReadingWithoutWhere,
        resultNodesCollected: SymbolicName,
        nodesCollected: SymbolicName,
        parentNodeId: Expression,
        options: NodeQueryOptions,
        totalCount: SymbolicName,
    ): StatementWithResultNodesAndNodes {
        val returnAlias = generateUniqueName()
        val returnBuilder = builder.returning(
            Cypher.asExpression(
                mapOf(
                    PARENT_NODE_ID_KEY to parentNodeId,
                    NODES_KEY to resultNodesCollected,
                    TOTAL_COUNT_KEY to if (options.fetchTotalCount) totalCount else Cypher.literalNull()
                )
            ).`as`(returnAlias), nodesCollected
        )
        return StatementWithResultNodesAndNodes(returnBuilder.build(), returnAlias, nodesCollected)
    }

    /**
     * Generates a new unique name
     */
    private fun generateUniqueName() = Cypher.name("a_${nameCounter++}")

    /**
     * Parses the result of a query (a list of nodes, and optional a totalCount).
     * Parses recursively
     *
     * @param record the complete result of the query
     * @param returnNodeName the name of the [record] entry which contains the main result of the query
     * @param nodeQuery the query used to get the results
     * @return the generated result
     */
    private fun parseQueryResultInternal(
        record: org.neo4j.driver.Record, returnNodeName: SymbolicName, nodeQuery: NodeQuery
    ): NodeQueryResult<*> {
        val value = record[returnNodeName.value]
        val partsResults = subQueryLookup.mapValues { (_, name) ->
            val entries = record[name]!!.asList { it }
            entries.associateBy { it[PARENT_NODE_ID_KEY].asString() }
        }
        return parseQueryResultInternal(value, nodeQuery, partsResults)
    }

    /**
     * Parses the result of a query (a list of nodes, and optional a totalCount)
     * Parses recursively
     *
     * @param value the main result of the query, contains the nodes and the optional totalCount
     * @param nodeQuery the query used to get the results
     * @param partsResults lookup from sub query and parent node id to related nodes (not parsed yet)
     */
    private fun parseQueryResultInternal(
        value: Value, nodeQuery: NodeQuery, partsResults: Map<NodeSubQuery, Map<String, Value>>
    ): NodeQueryResult<*> {
        val nodesValue = value[NODES_KEY]
        val totalCountValue = value[TOTAL_COUNT_KEY]
        val nodes = nodesValue.asList { parseNode(it, nodeQuery, partsResults) }
        val totalCount = if (totalCountValue.isNull) null else totalCountValue.asInt()
        return NodeQueryResult(nodeQuery.options, nodes, totalCount)
    }

    /**
     * Parses a single node
     * Parses SubQueries recursively
     *
     * @param value the returned value for the node
     * @param nodeQuery the query used to get the results
     * @param partsResults lookup from sub query and parent node id to related nodes (not parsed yet)
     * @return the parsed node
     */
    private fun parseNode(
        value: Value, nodeQuery: NodeQuery, partsResults: Map<NodeSubQuery, Map<String, Value>>
    ): io.github.graphglue.model.Node {
        val nodeId = value[NODE_KEY][0]["id"].asString()!!
        val node = nodeLookup.computeIfAbsent(nodeId) {
            mappingContext.entityConverter.read(nodeQuery.definition.nodeType.java, value[NODE_KEY])
        }
        for (subQuery in nodeQuery.parts.flatMap { it.value.subQueries }) {
            val nodesValue = partsResults[subQuery]!![nodeId]
            if (nodesValue != null) {
                val subQueryResult = parseQueryResultInternal(
                    nodesValue, subQuery.query, partsResults
                )
                val relationshipDefinition = subQuery.relationshipDefinition
                @Suppress("UNCHECKED_CAST") relationshipDefinition.registerQueryResult(
                    node, subQueryResult as NodeQueryResult<io.github.graphglue.model.Node>
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

/**
 * [Statement] with associated name for result nodes and all nodes
 *
 * @param statement the Cypher DSL statement of the SubQuery
 * @param allResultNodes name for return param with all nodes as result
 * @param allNodes name for return param with all raw nodes for parts SubQueries
 */
private data class StatementWithResultNodesAndNodes(
    val statement: Statement, val allResultNodes: SymbolicName, val allNodes: SymbolicName
)
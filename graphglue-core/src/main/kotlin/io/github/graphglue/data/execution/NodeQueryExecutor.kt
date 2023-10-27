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
 * @param client used to execute the query
 * @param mappingContext used to transform the result into a node
 */
class NodeQueryExecutor(
    private val client: ReactiveNeo4jClient, private val mappingContext: Neo4jMappingContext
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
     * lookup for generated ExtensionFields
     */
    private val extensionFieldLookup = HashMap<NodeExtensionField, String>()

    /**
     * Lookup table for already created nodes
     */
    private val nodeLookup = HashMap<String, io.github.graphglue.model.Node>()

    /**
     * Map of all nodes that were returned by a query
     */
    val returnedNodesByNodeQuery = HashMap<QueryBase<*>, MutableSet<io.github.graphglue.model.Node>>()

    /**
     * Executes the query
     *
     * @return the query result including all found nodes
     */
    suspend fun execute(query: NodeQuery): NodeQueryResult<*> {
        val (statement, returnName) = createRootNodeQuery(query)
        return client.query(Renderer.getDefaultRenderer().render(statement)).bindAll(statement.catalog.parameters)
            .fetchAs(NodeQueryResult::class.java).mappedBy { _, record ->
                parseQueryResultInternal(record, returnName, query).toCompleteResult(query)
            }.one().awaitSingle()
    }

    /**
     * Executes the query
     *
     * @return the query result including all found nodes
     */
    suspend fun execute(query: SearchQuery): SearchQueryResult<*> {
        val (statement, returnName) = createSearchRootQuery(query)
        return client.query(Renderer.getDefaultRenderer().render(statement)).bindAll(statement.catalog.parameters)
            .fetchAs(SearchQueryResult::class.java).mappedBy { _, record ->
                val partialResult = parseQueryResultInternal(record, returnName, query)
                SearchQueryResult(query.options, partialResult.nodes)
            }.one().awaitSingle()
    }

    /**
     * Executes a partial query and registers the results in [nodes]
     * Partial queries must not contain extension fields
     *
     * @param nodes the nodes for which to execute subqueries and where results are registered
     */
    suspend fun executePartial(query: PartialNodeQuery, nodes: Collection<io.github.graphglue.model.Node>) {
        if (query.parts.values.any { it.extensionFields.entries.isNotEmpty() }) {
            throw IllegalArgumentException("Partial queries with extension fields are not supported")
        }
        val (statement, returnName) = createPartialRootNodeQuery(query, nodes)
        nodes.forEach { nodeLookup[it.rawId!!] = it }
        client.query(Renderer.getDefaultRenderer().render(statement)).bindAll(statement.catalog.parameters)
            .fetchAs(PartialNodeQueryResult::class.java).mappedBy { _, record ->
                parseQueryResultInternal(record, returnName, query)
            }.one().awaitSingle()
    }

    /**
     * Generates a query based on `nodeQuery`
     * Must only be used for the root query
     *
     * @return the generated query and result column name
     */
    private fun createRootNodeQuery(query: NodeQuery): StatementWithSymbolicName {
        val node = query.definition.node().named(generateUniqueName().value)
        val builder = Cypher.match(node).with(node)
        val options = query.options
        val filteredBuilder = applyFilterConditions(options.filters, builder, node)
        val allNodesCollected = generateUniqueName()
        val collectedNodesBuilder = filteredBuilder.with(Functions.collect(node).`as`(allNodesCollected))
        val (totalCountBuilder, totalCount) = applyTotalCountIfRequired(
            options, collectedNodesBuilder, allNodesCollected, emptyList()
        )
        val (mainSubQuery, resultNodes, nodes) = generateMainSubQuery(query, node, allNodesCollected)
        val mainSubQueryBuilder = totalCountBuilder.call(mainSubQuery)
        val (subPartsBuilder, returnNames) = createPartsSubQueriesRecursive(mainSubQueryBuilder, query, nodes, 1)
        val (statement, returnName) = createRootReturnStatement(
            subPartsBuilder, resultNodes, returnNames, totalCount
        )
        return StatementWithSymbolicName(statement, returnName)
    }

    /**
     * Generates a search root query based
     *
     * @return the generated query and result column name
     */
    private fun createSearchRootQuery(query: SearchQuery): StatementWithSymbolicName {
        val nodeAlias = generateUniqueName()
        val node = query.definition.node().named(nodeAlias.value)
        val score = generateUniqueName()
        val builder = Cypher.call("db.index.fulltext.queryNodes").withArgs(
            Cypher.literalOf<String>(query.definition.searchIndexName!!), Cypher.anonParameter(query.options.query)
        ).yield(Cypher.name("node").`as`(nodeAlias), Cypher.name("score").`as`(score))
            .with(node, score)
        val filteredBuilder = applyFilterConditions(query.options.filters, builder, node).with(node, score, nodeAlias.`as`(query.definition.returnNodeName))
        val skippedBuilder = if (query.options.skip != null) {
            filteredBuilder.skip(query.options.skip)
        } else {
            filteredBuilder
        }
        val limitedBuilder = skippedBuilder.limit(Cypher.anonParameter(query.options.first))
        val resultNode = generateUniqueName()
        val resultNodeExpression = generateResultNodeExpression(query.definition, query, nodeAlias)
        val withResultBuilder = limitedBuilder.with(node, resultNodeExpression.`as`(resultNode))
        val resultNodes = generateUniqueName()
        val nodes = generateUniqueName()
        val collectedBuilder = withResultBuilder.with(
            Functions.collect(resultNode).`as`(resultNodes), Functions.collect(node).`as`(nodes)
        )
        val (subPartsBuilder, returnNames) = createPartsSubQueriesRecursive(collectedBuilder, query, nodes, 1)
        val (statement, returnName) = createRootReturnStatement(
            subPartsBuilder, resultNodes, returnNames, null
        )
        return StatementWithSymbolicName(statement, returnName)
    }

    /**
     * Creates a query based on `nodeQuery`
     * Must only be used for the root query
     * Does only include nodes in [nodes] and does only fetch the id of these nodes
     *
     * @param nodes the nodes for which to execute subqueries
     * @return the generated query and result column name
     */
    private fun createPartialRootNodeQuery(
        query: PartialNodeQuery, nodes: Collection<io.github.graphglue.model.Node>
    ): StatementWithSymbolicName {
        val node = query.definition.node().named(generateUniqueName().value)
        val builder = Cypher.match(node).where(node.property("id").`in`(Cypher.anonParameter(nodes.map { it.rawId })))
        val resultNodeName = generateUniqueName()
        val resultNodeMap =
            Cypher.asExpression(mapOf(NODE_KEY to Cypher.listOf(Cypher.asExpression(mapOf("id" to node.property("id"))))))
                .`as`(resultNodeName)
        val builderWithResult = builder.with(node, resultNodeMap)
        val allNodesCollected = generateUniqueName()
        val allResultNodesCollected = generateUniqueName()
        val collectedNodesBuilder = builderWithResult.with(
            Functions.collect(node).`as`(allNodesCollected),
            Functions.collect(resultNodeName).`as`(allResultNodesCollected)
        )
        val (subPartsBuilder, returnNames) = createPartsSubQueriesRecursive(
            collectedNodesBuilder, query, allNodesCollected, 1
        )
        val (statement, returnName) = createPartialRootReturnStatement(
            subPartsBuilder, allResultNodesCollected, returnNames
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
        builder: StatementBuilder.OngoingReading, nodeQuery: QueryBase<*>, allNodes: SymbolicName, unwindCount: Int
    ): Pair<StatementBuilder.OngoingReading, List<SymbolicName>> {
        var callBuilder = builder
        val returnNames = mutableListOf<SymbolicName>()
        for (part in nodeQuery.parts.values) {
            for (subQuery in part.subQueries.entries) {
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
     * @param totalCount name for the variable under which totalCount should be saved
     * @return the generated statement, the result name and the name of a collection with all raw nodes
     */
    private fun createRootReturnStatement(
        builder: StatementBuilder.OngoingReading,
        resultNodesCollected: SymbolicName,
        otherResults: List<SymbolicName>,
        totalCount: SymbolicName?,
    ): StatementWithSymbolicName {
        val returnAlias = generateUniqueName()
        val returnBuilder = builder.returning(
            listOf(
                Cypher.asExpression(
                    mapOf(
                        NODES_KEY to resultNodesCollected, TOTAL_COUNT_KEY to (totalCount ?: Cypher.literalNull())
                    )
                ).`as`(returnAlias)
            ) + otherResults
        )
        return StatementWithSymbolicName(returnBuilder.build(), returnAlias)
    }

    /**
     * Creates the return statement and builds the query.
     * This does NOT include total count and is meant for partial results
     *
     * @param builder ongoing statement builder
     * @param resultNodesCollected name for the variable containing a collection of all result nodes
     * @param otherResults names of other results which should be returned, e.g. the names of the SubQuery results
     * @return the generated statement, the result name and the name of a collection with all raw nodes
     */
    private fun createPartialRootReturnStatement(
        builder: StatementBuilder.OngoingReading,
        resultNodesCollected: SymbolicName,
        otherResults: List<SymbolicName>,
    ): StatementWithSymbolicName {
        val returnAlias = generateUniqueName()
        val returnBuilder = builder.returning(
            listOf(
                Cypher.asExpression(
                    mapOf(
                        NODES_KEY to resultNodesCollected,
                    )
                ).`as`(returnAlias)
            ) + otherResults
        )
        return StatementWithSymbolicName(returnBuilder.build(), returnAlias)
    }

    /**
     * Applies all filter conditions to a builder and returns the resulting builder
     *
     * @param filters list of filter conditions
     * @param builder builder for  Cypher-DSL query
     * @param node the node to generate the filter conditions for
     * @return the resulting builder
     */
    private fun applyFilterConditions(
        filters: List<CypherConditionGenerator>,
        builder: StatementBuilder.OrderableOngoingReadingAndWithWithoutWhere,
        node: Node
    ) = if (filters.isEmpty()) {
        builder
    } else {
        val filter = filters.fold(Conditions.noCondition()) { condition, filter ->
            condition.and(filter.generateCondition(node))
        }
        builder.where(filter)
    }

    /**
     * Wraps the builder with `applyTotalCount` if required by the options
     *
     * @param options the options for the query
     * @param builder the builder to wrap
     * @param allNodesCollected name of the collection containing all nodes
     * @param additionalNames additional names which should be added to the with statement
     * @return the new builder and the name of the variable containing the total count
     */
    private fun applyTotalCountIfRequired(
        options: NodeQueryOptions,
        builder: StatementBuilder.OrderableOngoingReadingAndWithWithoutWhere,
        allNodesCollected: SymbolicName,
        additionalNames: List<SymbolicName>
    ) = if (options.fetchTotalCount) {
        val totalCountName = generateUniqueName()
        applyTotalCount(builder, allNodesCollected, additionalNames, totalCountName) to totalCountName
    } else {
        builder to null
    }

    /**
     * If necessary, adds a with statement to the `builder` which fetches totalCount
     *
     * @param builder ongoing statement builder
     * @param allNodesCollected name for the variable containing a collection of all nodes
     * @param additionalNames additional names which should be added to the with statement
     * @param totalCount name for the variable under which totalCount should be saved
     * @return the new builder
     */
    private fun applyTotalCount(
        builder: StatementBuilder.OrderableOngoingReadingAndWithWithoutWhere,
        allNodesCollected: SymbolicName,
        additionalNames: List<SymbolicName>,
        totalCount: SymbolicName
    ) = builder.with(
        listOf(
            Functions.size(allNodesCollected).`as`(totalCount), allNodesCollected
        ) + additionalNames
    )

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
            nodeDefinition, limitedBuilder, nodeAlias, options, nodeQuery
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
     * @param nodeQuery the currently converted query
     * @return the generated statement, the result name and the name of a collection with all raw nodes
     */
    private fun generateMainSubQueryResultStatement(
        nodeDefinition: NodeDefinition,
        builder: StatementBuilder.OngoingReading,
        nodeAlias: SymbolicName,
        options: NodeQueryOptions,
        nodeQuery: NodeQuery
    ): StatementWithResultNodesAndNodes {
        val resultNodeExpression = generateResultNodeExpression(nodeDefinition, nodeQuery, nodeAlias)
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
     * Generates the expression for the result node, including the node itself and extension fields
     *
     * @param nodeDefinition definition for the currently queried node
     * @param nodeQuery the currently converted query
     * @param nodeAlias name of the variable containing the node
     * @return the generated expression
     */
    private fun generateResultNodeExpression(
        nodeDefinition: NodeDefinition, nodeQuery: QueryBase<*>, nodeAlias: SymbolicName
    ): MapExpression {
        val resultNodeMap = mapOf(NODE_KEY to Cypher.listOf(nodeDefinition.returnExpression)) + generateExtensionFields(
            nodeQuery, nodeAlias
        )
        return Cypher.asExpression(resultNodeMap)
    }

    /**
     * Generate a map to fetch extension fields for a NodeQuery.
     * Registers all extension fields in [extensionFieldLookup]
     *
     * @param nodeQuery the currently converted query
     * @param nodeAlias name of the variable containing the node
     * @return a map of extension field names to their expressions
     */
    private fun generateExtensionFields(nodeQuery: QueryBase<*>, nodeAlias: SymbolicName): Map<String, Expression> {
        val extensionFields = mutableMapOf<String, Expression>()
        nodeQuery.parts.values.forEach { part ->
            part.extensionFields.entries.forEach {
                val node = nodeQuery.definition.node().named(nodeAlias.value!!)
                val labelCondition = generateLabelCondition(node, it)
                val expression = it.definition.generateFetcher(it.dfe, it.field.arguments, node, nodeQuery.definition)
                val withLabelCheck = Cypher.caseExpression().`when`(labelCondition).then(expression)
                val fieldName = generateUniqueName().value!!
                extensionFields[fieldName] = withLabelCheck
                extensionFieldLookup[it] = fieldName
            }
        }
        return extensionFields
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
     * Orders the nodes and adds  first and last filters, and adds skip
     *
     * @param options defines first, last and skip
     * @param builder ongoing statement builder
     * @param nodeAlias name of the variable containing the node
     * @return the builder with first/last/skip applied
     */
    private fun applyResultLimiters(
        options: NodeQueryOptions,
        builder: StatementBuilder.OrderableOngoingReadingAndWithWithoutWhere,
        nodeAlias: SymbolicName
    ): StatementBuilder.OrderableOngoingReadingAndWithWithoutWhere {
        val firstOrLast = options.first ?: options.last
        return if (firstOrLast != null) {
            val orderedBuilder = if (options.first != null) {
                builder.orderBy(generateOrderFields(options.orderBy, nodeAlias, false)).with(nodeAlias)
            } else {
                builder.orderBy(generateOrderFields(options.orderBy, nodeAlias, true)).with(nodeAlias)
            }
            val skippedBuilder = if (options.skip != null) {
                orderedBuilder.skip(options.skip)
            } else {
                orderedBuilder
            }
            skippedBuilder.limit(firstOrLast).with(nodeAlias)
        } else {
            builder
        }
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
        val labelCondition = generateLabelCondition(node, subQuery)
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
     * Creates a condition to check that [node] has all labels specified in [entry] onlyOnTypes
     *
     * @param node the [Node] to generate the condition for
     * @param entry defining the labels to check for
     * @return the generated condition
     */
    private fun generateLabelCondition(node: Node, entry: NodeQueryPartEntry): Condition {
        return entry.onlyOnTypes.fold(Conditions.noCondition()) { condition, nodeDefinition ->
            condition.or(node.hasLabels(nodeDefinition.primaryLabel))
        }
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
        allNodes: SymbolicName, unwindCount: Int, parentNodeDefinition: NodeDefinition
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
        val filteredBuilder = applyFilterConditions(options.filters, builder, node)
        val allNodesCollected = generateUniqueName()
        val collectedNodesBuilder = filteredBuilder.with(Functions.collect(node).`as`(allNodesCollected), parentNode)
        val (totalCountBuilder, totalCount) = applyTotalCountIfRequired(
            options, collectedNodesBuilder, allNodesCollected, listOf(parentNode.requiredSymbolicName)
        )
        val (mainSubQuery, resultNodes, nodes) = generateMainSubQuery(nodeQuery, node, allNodesCollected)
        val mainSubQueryBuilder = totalCountBuilder.call(mainSubQuery)
        val parentId = parentNode.property("id")
        return createSubReturnStatement(mainSubQueryBuilder, resultNodes, nodes, parentId, totalCount)
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
     * @param totalCount name for the variable under which totalCount should be saved
     * @return the generated statement, the result name and the name of a collection with all raw nodes
     */
    private fun createSubReturnStatement(
        builder: StatementBuilder.OngoingReadingWithoutWhere,
        resultNodesCollected: SymbolicName,
        nodesCollected: SymbolicName,
        parentNodeId: Expression,
        totalCount: SymbolicName?,
    ): StatementWithResultNodesAndNodes {
        val returnAlias = generateUniqueName()
        val returnBuilder = builder.returning(
            Cypher.asExpression(
                mapOf(
                    PARENT_NODE_ID_KEY to parentNodeId,
                    NODES_KEY to resultNodesCollected,
                    TOTAL_COUNT_KEY to (totalCount ?: Cypher.literalNull())
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
        record: org.neo4j.driver.Record, returnNodeName: SymbolicName, nodeQuery: QueryBase<*>
    ): PartialNodeQueryResult {
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
        value: Value, nodeQuery: QueryBase<*>, partsResults: Map<NodeSubQuery, Map<String, Value>>
    ): PartialNodeQueryResult {
        val nodesValue = value[NODES_KEY]
        val totalCountValue = value[TOTAL_COUNT_KEY]
        val nodes = nodesValue.asList { parseNode(it, nodeQuery, partsResults) }
        returnedNodesByNodeQuery.getOrPut(nodeQuery) { mutableSetOf() } += nodes
        val totalCount = if (totalCountValue.isNull) null else totalCountValue.asInt()
        return PartialNodeQueryResult(nodes, totalCount)
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
        value: Value, nodeQuery: QueryBase<*>, partsResults: Map<NodeSubQuery, Map<String, Value>>
    ): io.github.graphglue.model.Node {
        val nodeId = value[NODE_KEY][0]["id"].asString()!!
        val node = nodeLookup.computeIfAbsent(nodeId) {
            mappingContext.entityConverter.read(nodeQuery.definition.nodeType.java, value[NODE_KEY])
        }
        for (subQuery in nodeQuery.parts.flatMap { it.value.subQueries.entries }) {
            val nodesValue = partsResults[subQuery]!![nodeId]
            if (nodesValue != null) {
                val partialQueryResult = parseQueryResultInternal(nodesValue, subQuery.query, partsResults)
                val subQueryResult = partialQueryResult.toCompleteResult(subQuery.query)
                val relationshipDefinition = subQuery.relationshipDefinition
                relationshipDefinition.registerQueryResult(node, subQueryResult)
            }
        }
        if (node.extensionFields == null) {
            node.extensionFields = mutableMapOf()
        }
        val extensionFields = node.extensionFields!!
        for (extensionField in nodeQuery.parts.flatMap { it.value.extensionFields.entries }) {
            val name = extensionFieldLookup[extensionField]!!
            val extensionFieldValue = value[name]
            if (!extensionFieldValue.isNull) {
                val transformedExtensionFieldValue = extensionField.definition.transformResult(extensionFieldValue)
                extensionFields[extensionField.resultKey] = transformedExtensionFieldValue
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

/**
 * Result of a query
 *
 * @param nodes the nodes returned by the query
 * @param totalCount the total count of nodes, if requested
 */
private data class PartialNodeQueryResult(val nodes: List<io.github.graphglue.model.Node>, val totalCount: Int?) {
    /**
     * Converts the result to a [NodeQueryResult]
     *
     * @param nodeQuery the query used to get the result
     * @return the converted result
     */
    fun toCompleteResult(nodeQuery: NodeQuery) = NodeQueryResult(nodeQuery.options, nodes, totalCount)
}
package io.github.graphglue.data.execution

import io.github.graphglue.connection.order.Order
import io.github.graphglue.connection.order.OrderDirection
import io.github.graphglue.definition.NodeDefinition
import kotlinx.coroutines.reactor.awaitSingle
import org.neo4j.cypherdsl.core.*
import org.neo4j.cypherdsl.core.StatementBuilder.OrderableOngoingReadingAndWithWithWhere
import org.neo4j.cypherdsl.core.StatementBuilder.OrderableOngoingReadingAndWithWithoutWhere
import org.neo4j.cypherdsl.core.renderer.Renderer
import org.neo4j.driver.Value
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext

/**
 * Name for the single node map entry
 */
const val NODE_KEY = "node"

/**
 * Name for the order map entry
 */
const val ORDER_KEY = "order"

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
 * @param renderer used to render the Cypher-DSL queries
 */
class NodeQueryExecutor(
    private val client: ReactiveNeo4jClient,
    private val mappingContext: Neo4jMappingContext,
    private val renderer: Renderer
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
     * @param query defines the query to execute
     * @return the query result including all found nodes
     */
    suspend fun execute(query: NodeQuery): NodeQueryResult<*> {
        val (statement, returnName) = createRootNodeQuery(query)
        return client.query(renderer.render(statement)).bindAll(statement.catalog.parameters)
            .fetchAs(NodeQueryResult::class.java).mappedBy { _, record ->
                parseQueryResultInternal(record, returnName, query).toCompleteResult(query)
            }.one().awaitSingle()
    }

    /**
     * Executes the query
     *
     * @param query defines the query to execute
     * @return the query result including all found nodes
     */
    suspend fun execute(query: SearchQuery): SearchQueryResult<*> {
        val (statement, returnName) = createSearchRootQuery(query)
        return client.query(renderer.render(statement)).bindAll(statement.catalog.parameters)
            .fetchAs(SearchQueryResult::class.java).mappedBy { _, record ->
                val partialResult = parseQueryResultInternal(record, returnName, query)
                SearchQueryResult(query.options, partialResult.nodes)
            }.one().awaitSingle()
    }

    /**
     * Executes a partial query and registers the results in [nodes]
     *
     * @param query defines the query to execute
     * @param nodes the nodes for which to execute subqueries and where results are registered
     */
    suspend fun executePartial(query: PartialNodeQuery, nodes: Collection<io.github.graphglue.model.Node>) {
        val (statement, returnName) = createPartialRootNodeQuery(query, nodes)
        nodes.forEach { nodeLookup[it.id!!] = it }
        client.query(renderer.render(statement)).bindAll(statement.catalog.parameters)
            .fetchAs(PartialNodeQueryResult::class.java).mappedBy { _, record ->
                parseQueryResultInternal(record, returnName, query)
            }.one().awaitSingle()
    }

    /**
     * Generates a query based on `nodeQuery`
     * Must only be used for the root query
     *
     * @param query defines the query to execute
     * @return the generated query and result column name
     */
    private fun createRootNodeQuery(query: NodeQuery): StatementWithSymbolicName {
        val nodeAlias = generateUniqueName()
        val node = query.definition.node().named(nodeAlias)
        val builder = Cypher.match(node).with(node)
        val options = query.options
        val filteredBuilder = applyFilterConditions(options.filters, builder, node)
        val allNodesCollected = generateUniqueName()
        val collectedNodesBuilder = filteredBuilder.with(Cypher.collect(node).`as`(allNodesCollected))
        val (totalCountBuilder, totalCount) = applyTotalCountIfRequired(
            options, collectedNodesBuilder, allNodesCollected, emptyList()
        )
        val (mainSubQuery, resultNodes, nodes) = generateMainSubQuery(query, node, allNodesCollected)
        val mainSubQueryBuilder = totalCountBuilder.call(mainSubQuery, allNodesCollected)
        val (subQueriesBuilder, returnNames) = createEntriesSubQueriesRecursive(mainSubQueryBuilder, query, nodes, 1)
        val (statement, returnName) = createRootReturnStatement(
            subQueriesBuilder, resultNodes, returnNames, totalCount
        )
        return StatementWithSymbolicName(statement, returnName)
    }

    /**
     * Generates a search root query based
     *
     * @param query defines the query to execute
     * @return the generated query and result column name
     */
    private fun createSearchRootQuery(query: SearchQuery): StatementWithSymbolicName {
        val nodeAlias = generateUniqueName()
        val node = query.definition.node().named(nodeAlias.value)
        val score = generateUniqueName()
        val builder = Cypher.call("db.index.fulltext.queryNodes").withArgs(
            Cypher.literalOf<String>(query.definition.searchIndexName!!), Cypher.anonParameter(query.options.query)
        ).yield(Cypher.name("node").`as`(nodeAlias), Cypher.name("score").`as`(score)).with(node, score)
        val filteredBuilder = applyFilterConditions(query.options.filters, builder, node).with(
            node, score, nodeAlias.`as`(query.definition.returnNodeName)
        )
        val skippedBuilder = if (query.options.skip != null) {
            filteredBuilder.skip(query.options.skip)
        } else {
            filteredBuilder
        }
        val limitedBuilder = skippedBuilder.limit(Cypher.anonParameter(query.options.first))
        val resultNode = generateUniqueName()
        val resultNodeExpression =
            generateResultNodeExpression(query.definition.returnExpression, query, nodeAlias, null)
        val withResultBuilder = limitedBuilder.with(node, resultNodeExpression.`as`(resultNode))
        val resultNodes = generateUniqueName()
        val nodes = generateUniqueName()
        val collectedBuilder = withResultBuilder.with(
            Cypher.collect(resultNode).`as`(resultNodes), Cypher.collect(node).`as`(nodes)
        )
        val (subQueriesBuilder, returnNames) = createEntriesSubQueriesRecursive(collectedBuilder, query, nodes, 1)
        val (statement, returnName) = createRootReturnStatement(
            subQueriesBuilder, resultNodes, returnNames, null
        )
        return StatementWithSymbolicName(statement, returnName)
    }

    /**
     * Creates a query based on `nodeQuery`
     * Must only be used for the root query
     * Does only include nodes in [nodes] and does only fetch the id of these nodes
     *
     * @param query defines the query to execute
     * @param nodes the nodes for which to execute subqueries
     * @return the generated query and result column name
     */
    private fun createPartialRootNodeQuery(
        query: PartialNodeQuery, nodes: Collection<io.github.graphglue.model.Node>
    ): StatementWithSymbolicName {
        val nodeAlias = generateUniqueName()
        val node = query.definition.node().named(nodeAlias)
        val builder = Cypher.match(node).where(node.property("id").`in`(Cypher.anonParameter(nodes.map { it.rawId })))
        val resultNodeName = generateUniqueName()
        val resultNodeMap = generateResultNodeExpression(
            Cypher.asExpression(mapOf("id" to node.property("id"))), query, nodeAlias, null
        ).`as`(resultNodeName)
        val builderWithResult = builder.with(node, resultNodeMap)
        val allNodesCollected = generateUniqueName()
        val allResultNodesCollected = generateUniqueName()
        val collectedNodesBuilder = builderWithResult.with(
            Cypher.collect(node).`as`(allNodesCollected), Cypher.collect(resultNodeName).`as`(allResultNodesCollected)
        )
        val (subQueriesBuilder, returnNames) = createEntriesSubQueriesRecursive(
            collectedNodesBuilder, query, allNodesCollected, 1
        )
        val (statement, returnName) = createPartialRootReturnStatement(
            subQueriesBuilder, allResultNodesCollected, returnNames
        )
        return StatementWithSymbolicName(statement, returnName)
    }

    /**
     * Creates SubQueries for each SubQuery in the provided [nodeQuery]
     * Generates recursively depth first.
     *
     * @param builder the builder to add the SubQuery calls to
     * @param nodeQuery contains the SubQuery entries
     * @param allNodes name of the collection containing all nodes, should be a nested collection of depth [unwindCount]
     * @param unwindCount nesting depth of [allNodes] collection
     * @return the new statement builder, and a list of all names to return (names of the SubQuery results)
     */
    private fun createEntriesSubQueriesRecursive(
        builder: StatementBuilder.OngoingReading, nodeQuery: QueryBase<*>, allNodes: SymbolicName, unwindCount: Int
    ): Pair<StatementBuilder.OngoingReading, List<SymbolicName>> {
        var callBuilder = builder
        val returnNames = mutableListOf<SymbolicName>()
        for (subQuery in nodeQuery.entries.filterIsInstance<NodeSubQuery>()) {
            val parentDefinition = nodeQuery.definition
            val (statement, resultNodes, nodes) = createSubQuery(subQuery, parentDefinition, allNodes, unwindCount)
            returnNames += resultNodes
            callBuilder = callBuilder.call(statement, allNodes)
            subQueryLookup[subQuery] = resultNodes.value!!
            val (newBuilder, newReturnNames) = createEntriesSubQueriesRecursive(callBuilder, subQuery.query, nodes, 2)
            callBuilder = newBuilder
            returnNames += newReturnNames
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
        val filter = filters.fold(Cypher.noCondition()) { condition, filter ->
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
        builder: OrderableOngoingReadingAndWithWithoutWhere,
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
        builder: OrderableOngoingReadingAndWithWithoutWhere,
        allNodesCollected: SymbolicName,
        additionalNames: List<SymbolicName>,
        totalCount: SymbolicName
    ) = builder.with(
        listOf(
            Cypher.size(allNodesCollected).`as`(totalCount), allNodesCollected
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
        if (nodeQuery.options.ignoreNodes) {
            return generateEmptyMainSubQueryResultStatement()
        }
        val options = nodeQuery.options
        val nodeAlias = node.requiredSymbolicName
        val nodeDefinition = nodeQuery.definition
        val subQueryBuilder = Cypher.unwind(allNodesCollected).`as`(nodeAlias)
        val (afterAndBeforeBuilder, orderContext) = applyAfterAndBefore(options, nodeAlias, subQueryBuilder)
        val limitedBuilder = applyResultLimiters(orderContext, options, afterAndBeforeBuilder, nodeAlias)
        return generateMainSubQueryResultStatement(
            nodeDefinition, limitedBuilder, nodeAlias, orderContext, nodeQuery
        )
    }

    /**
     * Generates a subquery res which return no nodes
     *
     * @return the generated statement, and two names referring to empty collections
     */
    private fun generateEmptyMainSubQueryResultStatement(): StatementWithResultNodesAndNodes {
        val collectedResultNodes = generateUniqueName()
        val collectedNodes = generateUniqueName()
        val statement = Cypher.with(Cypher.listOf().`as`(collectedResultNodes), Cypher.listOf().`as`(collectedNodes))
            .returning(collectedResultNodes, collectedNodes).build()
        return StatementWithResultNodesAndNodes(
            statement, collectedResultNodes, collectedNodes
        )
    }

    /**
     * Generates the result statement for the query generated by [generateMainSubQuery].
     * Also orders the nodes, and builds a statement out of the builder
     *
     * @param nodeDefinition definition for the currently queried node
     * @param builder ongoing statement builder
     * @param nodeAlias name of the variable containing the node
     * @param orderContext order to sort by with associated variables
     * @param nodeQuery the currently converted query
     * @return the generated statement, the result name and the name of a collection with all raw nodes
     */
    private fun generateMainSubQueryResultStatement(
        nodeDefinition: NodeDefinition,
        builder: StatementBuilder.OngoingReading,
        nodeAlias: SymbolicName,
        orderContext: OrderContext?,
        nodeQuery: NodeQuery
    ): StatementWithResultNodesAndNodes {
        val resultNodeExpression = generateResultNodeExpression(
            nodeDefinition.returnExpression, nodeQuery, nodeAlias, orderContext
        )
        val resultNode = generateUniqueName()
        val orderVariables = orderContext?.variables?.values ?: emptyList()
        val resultBuilder = builder.with(
            listOf(
                nodeAlias.`as`(nodeDefinition.returnNodeName), nodeAlias
            ) + orderVariables
        ).with(listOf(resultNodeExpression.`as`(resultNode), nodeAlias) + orderVariables)
        val collectedResultNodes = generateUniqueName()
        val collectedNodes = generateUniqueName()
        val statement = if (orderContext != null) {
            resultBuilder.orderBy(generateOrderFields(orderContext))
        } else {
            resultBuilder
        }.returning(
            Cypher.collect(resultNode).`as`(collectedResultNodes), Cypher.collect(nodeAlias).`as`(collectedNodes)
        ).build()
        return StatementWithResultNodesAndNodes(statement, collectedResultNodes, collectedNodes)
    }

    /**
     * Generates the expression for the result node, including the node itself and extension fields
     *
     * @param nodeExpression the expression for the node itself
     * @param nodeQuery the currently converted query
     * @param nodeAlias name of the variable containing the node
     * @param orderContext order to sort by with associated variables
     * @return the generated expression
     */
    private fun generateResultNodeExpression(
        nodeExpression: Expression, nodeQuery: QueryBase<*>, nodeAlias: SymbolicName, orderContext: OrderContext?
    ): MapExpression {
        val resultNodeMap = mapOf(
            NODE_KEY to Cypher.listOf(nodeExpression),
            ORDER_KEY to Cypher.asExpression(orderContext?.variables ?: emptyMap())
        ) + generateExtensionFields(nodeQuery, nodeAlias)
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
        nodeQuery.entries.filterIsInstance<NodeExtensionField>().forEach {
            val node = nodeQuery.definition.node().named(nodeAlias.value!!)
            val labelCondition = generateLabelCondition(node, it)
            val expression = it.fieldDefinition.generateFetcher(it.dfe, it.arguments, node, nodeQuery.definition)
            val withLabelCheck = Cypher.caseExpression().`when`(labelCondition).then(expression)
            val fieldName = generateUniqueName().value!!
            extensionFields[fieldName] = withLabelCheck
            extensionFieldLookup[it] = fieldName
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
    ): Pair<OrderableOngoingReadingAndWithWithoutWhere, OrderContext?> {
        val orderContext = if (options.orderBy != null) {
            OrderContext(options.orderBy, options.orderBy.fields.associate { it.part.name to generateUniqueName() })
        } else {
            null
        }
        val orderVariables = orderContext?.order?.fields?.map {
            it.part.getExpression(nodeAlias).`as`(orderContext.variables[it.part.name])
        } ?: emptyList()
        val builderWithOrderVariables = builder.with(orderVariables + nodeAlias)
        if (options.after != null || options.before != null) {
            require(orderContext != null) { "Can't use after/before without orderBy" }
            var filterCondition = Cypher.noCondition()
            if (options.after != null) {
                filterCondition = filterCondition.and(
                    generateCursorFilterExpression(options.after, orderContext, true)
                )
            }
            if (options.before != null) {
                filterCondition = filterCondition.and(
                    generateCursorFilterExpression(options.before, orderContext, false)
                )
            }
            return builderWithOrderVariables.where(filterCondition)
                .with(orderContext.variables.values + nodeAlias) to orderContext
        } else {
            return builderWithOrderVariables to orderContext
        }
    }

    /**
     * Adds first and last filters, and adds skip.
     * Requires that the nodes are already correctly ordered
     *
     * @param orderContext order with variables
     * @param options defines first, last and skip
     * @param builder ongoing statement builder
     * @param nodeAlias name of the variable containing the node
     * @return the builder with first/last/skip applied
     */
    private fun applyResultLimiters(
        orderContext: OrderContext?,
        options: NodeQueryOptions,
        builder: OrderableOngoingReadingAndWithWithoutWhere,
        nodeAlias: SymbolicName
    ): OrderableOngoingReadingAndWithWithoutWhere {
        val firstOrLast = options.first ?: options.last
        return if (firstOrLast != null) {
            val orderedBuilder = if (orderContext != null) {
                if (options.first != null) {
                    builder.orderBy(generateOrderFields(orderContext, false))
                } else {
                    builder.orderBy(generateOrderFields(orderContext, true))
                }
            } else {
                builder
            }
            val skippedBuilder = if (options.skip != null) {
                orderedBuilder.skip(options.skip)
            } else {
                orderedBuilder
            }
            val allVariables = (orderContext?.variables?.values ?: emptyList()) + nodeAlias
            skippedBuilder.limit(firstOrLast).with(allVariables)
        } else {
            builder
        }
    }

    /**
     * Generates a [Condition] which can be used to filter for items before/after a specific cursor
     *
     * @param cursor the parsed cursor
     * @param orderContext order in which the nodes will be sorted with variables, necessary to interpret cursor
     * @param forwards if `true`, filters for items after the cursor, otherwise for items before the cursor
     *                 (both inclusive)
     * @return an [Expression] which can be used to filter for items after/before the provided `cursor`
     */
    private fun generateCursorFilterExpression(
        cursor: Map<String, Any?>, orderContext: OrderContext, forwards: Boolean
    ): Condition {
        return orderContext.order.fields.asReversed()
            .foldIndexed(Cypher.noCondition()) { index, filterExpression, field ->
                val part = field.part
                val realForwards = if (field.direction == OrderDirection.ASC) forwards else !forwards
                var newFilterExpression = filterExpression
                val expression = orderContext.variables[part.name]!!
                val value = cursor[part.name]
                val propertyValue = Cypher.anonParameter<Any?>(value)
                if (index > 0) {
                    val eqCondition = if (value != null) expression.eq(propertyValue) else expression.isNull
                    newFilterExpression = eqCondition.and(newFilterExpression)
                }
                val neqCondition = if (value == null) {
                    expression.isNotNull
                } else {
                    if (realForwards) expression.gt(propertyValue) else expression.lt(propertyValue)
                }
                if (!part.isNullable || !realForwards) {
                    neqCondition.or(newFilterExpression)
                } else if (value == null) {
                    newFilterExpression
                } else {
                    neqCondition.or(expression.isNull).or(newFilterExpression)
                }
            }
    }

    /**
     * Transforms an [Order] into  a list of [SortItem]
     *
     * @param orderContext the [Order] to transform
     * @param reversed if `true`, the direction defined by `order` is reversed
     * @return the list of generated [SortItem]
     */
    private fun generateOrderFields(
        orderContext: OrderContext, reversed: Boolean = false
    ): List<SortItem> {
        return orderContext.order.fields.map {
            val direction = if ((it.direction == OrderDirection.ASC) != reversed) {
                SortItem.Direction.ASC
            } else {
                SortItem.Direction.DESC
            }
            Cypher.sort(orderContext.variables[it.part.name]!!, direction)
        }
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
        val innerBuilder = applySubQueryMatchWithAuthorizationConditions(
            Cypher.with(node).where(labelCondition), node, relatedNode, subQuery.relationshipDefinitions
        )
        val (innerStatement, innerResultNodes, innerNodes) = createSubNodeQuery(
            nodeQuery, innerBuilder, relatedNode, node
        )
        val resultNodes = generateUniqueName()
        val nodes = generateUniqueName()
        return StatementWithResultNodesAndNodes(
            builder.with(node).call(innerStatement, node).returning(
                Cypher.collect(innerResultNodes).`as`(resultNodes), Cypher.collect(innerNodes).`as`(nodes)
            ).build(), resultNodes, nodes
        )
    }

    /**
     * Matches the relationships defined in [relationshipDefinitions] and applies authorization conditions
     *
     * @param builder the builder to apply the conditions to
     * @param node the node to generate the conditions for
     * @param relatedNode the node to which the relationships should be matched
     * @param relationshipDefinitions the chain of relationships to match
     * @return the new builder
     */
    private fun applySubQueryMatchWithAuthorizationConditions(
        builder: OrderableOngoingReadingAndWithWithWhere,
        node: Node,
        relatedNode: Node,
        relationshipDefinitions: List<AuthorizedRelationDefinition>
    ): OrderableOngoingReadingAndWithWithoutWhere {
        val relationNodes = relationshipDefinitions.dropLast(1).map {
            it.relatedNodeDefinition.node().named(generateUniqueName())
        } + relatedNode
        val definitionsWithNodes = relationshipDefinitions.zip(relationNodes)
        val authCondition = definitionsWithNodes.mapNotNull { (definition, node) ->
            definition.authorizationCondition?.generateCondition(node)
        }.fold(Cypher.isTrue()) { acc, condition ->
            acc.and(condition)
        }
        var relationship: RelationshipPattern =
            relationshipDefinitions.first().relationshipDefinition.generateRelationship(
                node, relationNodes.first()
            )
        definitionsWithNodes.drop(1).forEach { (definition, node) ->
            relationship = definition.relationshipDefinition.generateRelationship(relationship, node)
        }
        return builder.match(relationship).where(authCondition).withDistinct(node, relatedNode)
    }

    /**
     * Creates a condition to check that [node] has all labels specified in [entry] onlyOnTypes
     *
     * @param node the [Node] to generate the condition for
     * @param entry defining the labels to check for
     * @return the generated condition
     */
    private fun generateLabelCondition(node: Node, entry: NodeQueryEntry<*>): Condition {
        if (entry.onlyOnTypes == null) {
            return Cypher.isTrue()
        }
        return entry.onlyOnTypes.fold(Cypher.noCondition()) { condition, nodeDefinition ->
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
        nodeQuery: NodeQuery, builder: OrderableOngoingReadingAndWithWithoutWhere, node: Node, parentNode: Node
    ): StatementWithResultNodesAndNodes {
        val options = nodeQuery.options
        val filteredBuilder = applyFilterConditions(options.filters, builder, node)
        val allNodesCollected = generateUniqueName()
        val collectedNodesBuilder = filteredBuilder.with(Cypher.collect(node).`as`(allNodesCollected), parentNode)
        val (totalCountBuilder, totalCount) = applyTotalCountIfRequired(
            options, collectedNodesBuilder, allNodesCollected, listOf(parentNode.requiredSymbolicName)
        )
        val (mainSubQuery, resultNodes, nodes) = generateMainSubQuery(nodeQuery, node, allNodesCollected)
        val mainSubQueryBuilder = totalCountBuilder.call(mainSubQuery, allNodesCollected)
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
     * @param nodesCollected name for the variable containing all raw nodes, used for SubQueries
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
        val entriesResults = subQueryLookup.mapValues { (_, name) ->
            val entries = record[name]!!.asList { it }
            entries.associateBy { it[PARENT_NODE_ID_KEY].asString() }
        }
        return parseQueryResultInternal(value, nodeQuery, entriesResults)
    }

    /**
     * Parses the result of a query (a list of nodes, and optional a totalCount)
     * Parses recursively
     *
     * @param value the main result of the query, contains the nodes and the optional totalCount
     * @param nodeQuery the query used to get the results
     * @param entriesResults lookup from sub query and parent node id to related nodes (not parsed yet)
     */
    private fun parseQueryResultInternal(
        value: Value, nodeQuery: QueryBase<*>, entriesResults: Map<NodeSubQuery, Map<String, Value>>
    ): PartialNodeQueryResult {
        val nodesValue = value[NODES_KEY]
        val totalCountValue = value[TOTAL_COUNT_KEY]
        val nodes = nodesValue.asList { parseNode(it, nodeQuery, entriesResults) }
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
     * @param entriesResults lookup from sub query and parent node id to related nodes (not parsed yet)
     * @return the parsed node
     */
    private fun parseNode(
        value: Value, nodeQuery: QueryBase<*>, entriesResults: Map<NodeSubQuery, Map<String, Value>>
    ): io.github.graphglue.model.Node {
        val nodeId = value[NODE_KEY][0]["id"].asString()!!
        val node = nodeLookup.computeIfAbsent(nodeId) {
            mappingContext.entityConverter.read(nodeQuery.definition.nodeType.java, value[NODE_KEY])
        }
        parseNodeEntries(nodeQuery, node, entriesResults, value)
        if (node.orderFields == null) {
            node.orderFields = mutableMapOf()
        }
        val orderFields = node.orderFields!!
        val orderValue = value[ORDER_KEY]
        if (!orderValue.isNull) {
            for ((fieldName, fieldValue) in orderValue.asMap()) {
                if (fieldName !in orderFields) {
                    orderFields[fieldName] = fieldValue
                }
            }
        }
        return node
    }

    /**
     * Parses the entries of a [node] based on the provided [nodeQuery]
     *
     * @param nodeQuery defines the entries to parse
     * @param node the node of which the entries should be parsed
     * @param entriesResults lookup from sub query and parent node id to related nodes (not parsed yet)
     * @param value the return value for the current [node]
     */
    private fun parseNodeEntries(
        nodeQuery: QueryBase<*>,
        node: io.github.graphglue.model.Node,
        entriesResults: Map<NodeSubQuery, Map<String, Value>>,
        value: Value
    ) {
        for (entry in nodeQuery.entries) {
            if (entry is NodeSubQuery && entry.affectsNode(node)) {
                val entries = entriesResults[entry]!!
                val subQueryResult = if (node.id in entries) {
                    val nodesValue = entries[node.id]!!
                    val partialQueryResult = parseQueryResultInternal(nodesValue, entry.query, entriesResults)
                    partialQueryResult.toCompleteResult(entry.query)
                } else {
                    NodeQueryResult(entry.query.options, emptyList(), 0)
                }
                node.registerQueryResult(entry, subQueryResult)
            }
            if (entry is NodeExtensionField) {
                val name = extensionFieldLookup[entry]!!
                if (value.containsKey(name)) {
                    val extensionFieldValue = entry.fieldDefinition.transformResult(value[name])
                    node.registerQueryResult(entry, extensionFieldValue)
                }
            }
        }
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
 * @param allNodes name for return param with all raw nodes for entries SubQueries
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

/**
 * Wrapper for an [Order] and the variables used in it
 *
 * @param order the order to generate
 * @param variables the variables used in the order
 */
private data class OrderContext(val order: Order<*>, val variables: Map<String, SymbolicName>)
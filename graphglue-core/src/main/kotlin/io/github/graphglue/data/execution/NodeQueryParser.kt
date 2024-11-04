package io.github.graphglue.data.execution

import com.fasterxml.jackson.databind.ObjectMapper
import graphql.schema.DataFetchingEnvironment
import graphql.schema.SelectedField
import io.github.graphglue.authorization.Permission
import io.github.graphglue.connection.filter.definition.FilterDefinition
import io.github.graphglue.connection.filter.definition.FilterDefinitionCollection
import io.github.graphglue.connection.order.IdOrder
import io.github.graphglue.connection.order.parseOrder
import io.github.graphglue.definition.*
import io.github.graphglue.graphql.extensions.requiredPermission
import io.github.graphglue.model.Node


/**
 * Amount of nodes which are fetched additionally to determine if there are more nodes to come
 */
private const val NODE_FETCH_OFFSET = 1

/**
 * Amount of nodes which are fetched when fetching a one-side of a query
 * Fetches two nodes to detect an inconsistent state in the database
 */
const val ONE_NODE_QUERY_LIMIT = 2

/**
 * Parser to get [NodeQuery]s
 * Can be used to create queries which load a subtree of nodes in one query
 *
 * @param nodeDefinitionCollection used to get the [NodeDefinition] for a specific [Node]
 * @param filterDefinitionCollection used to get the [FilterDefinition] for a specific [Node], if existing
 * @param objectMapper used to parse cursors
 */
class NodeQueryParser(
    val nodeDefinitionCollection: NodeDefinitionCollection,
    val filterDefinitionCollection: FilterDefinitionCollection?,
    val objectMapper: ObjectMapper
) {

    /**
     * Generates a [NodeSubQuery] for the provided [fieldDefinition]
     *
     * @param fieldDefinition the field to generate the subquery for
     * @param relationshipDefinitions the relationship path the field represents
     * @param context provides the sub-selection set, result path and arguments
     * @param dataFetchingEnvironment used to access the authorization permission, may not be in the context of
     *                                the field represented by fieldDefinition
     * @param onlyOnTypes if provided, types for which the subquery is active
     */
    fun generateSubQuery(
        fieldDefinition: FieldDefinition,
        relationshipDefinitions: List<RelationshipDefinition>,
        context: FieldFetchingContext,
        dataFetchingEnvironment: DataFetchingEnvironment,
        onlyOnTypes: List<NodeDefinition>?
    ): NodeSubQuery {
        val instance = NodeQueryParserInstance(dataFetchingEnvironment)
        val requiredPermission = dataFetchingEnvironment.requiredPermission
        val authorizedRelationshipDefinitions =
            getAuthorizedRelationshipDefinitions(relationshipDefinitions, requiredPermission)
        return instance.generateSubQuery(
            fieldDefinition, authorizedRelationshipDefinitions, context, requiredPermission, onlyOnTypes
        )
    }

    /**
     * Generates a [NodeQuery] which loads a single [Node]
     * Can use the `dataFetchingEnvironment` to fetch a subtree of node
     *
     * @param definition [NodeDefinition] of the node to load
     * @param dataFetchingEnvironment can optionally be provided to fetch a subtree of nodes
     * @param additionalConditions list of conditions which are applied to filter the returned node
     * @param requiredPermission optional required permission
     * @return the generated [NodeQuery] to load the node
     */
    fun generateOneNodeQuery(
        definition: NodeDefinition,
        dataFetchingEnvironment: DataFetchingEnvironment,
        additionalConditions: List<CypherConditionGenerator>,
        requiredPermission: Permission?
    ): NodeQuery {
        val authorizationCondition = getAuthorizationCondition(requiredPermission, definition)
        val instance = NodeQueryParserInstance(dataFetchingEnvironment)
        return instance.generateOneNodeQuery(
            definition, FieldFetchingContext.from(dataFetchingEnvironment), additionalConditions, authorizationCondition
        )
    }

    /**
     * Generates a [NodeQuery] which loads multiple [Node]s
     * Can use the `dataFetchingEnvironment` to fetch a subtree of node
     *
     * @param definition [NodeDefinition] of the nodes to load
     * @param dataFetchingEnvironment can optionally be provided to fetch a subtree of nodes
     * @param additionalConditions list of conditions which are applied to filter the returned node
     * @param requiredPermission optional required permission
     * @return the generated [NodeQuery] to load the node
     */
    fun generateManyNodeQuery(
        definition: NodeDefinition,
        dataFetchingEnvironment: DataFetchingEnvironment,
        additionalConditions: List<CypherConditionGenerator>,
        requiredPermission: Permission?
    ): NodeQuery {
        val authorizationCondition = getAuthorizationCondition(requiredPermission, definition)
        val instance = NodeQueryParserInstance(dataFetchingEnvironment)
        return instance.generateManyNodeQuery(
            definition,
            FieldFetchingContext.from(dataFetchingEnvironment),
            additionalConditions,
            requiredPermission,
            authorizationCondition
        )
    }

    /**
     * Generates a [SearchQuery] which loads multiple [Node]s
     * Can use the `dataFetchingEnvironment` to fetch a subtree of node
     *
     * @param definition [NodeDefinition] of the nodes to load
     * @param dataFetchingEnvironment can optionally be provided to fetch a subtree of nodes
     * @param requiredPermission optional required permission
     * @return the generated [SearchQuery] to load the nodes
     */
    fun generateSearchQuery(
        definition: NodeDefinition, dataFetchingEnvironment: DataFetchingEnvironment, requiredPermission: Permission?
    ): SearchQuery {
        val authorizationCondition = getAuthorizationCondition(requiredPermission, definition)
        val instance = NodeQueryParserInstance(dataFetchingEnvironment)
        return instance.generateSearchQuery(
            definition, FieldFetchingContext.from(dataFetchingEnvironment), requiredPermission, authorizationCondition
        )
    }

    /**
     * Helper class providing the [DataFetchingEnvironment]
     *
     * @param dataFetchingEnvironment the [DataFetchingEnvironment] to use
     */
    internal inner class NodeQueryParserInstance(private val dataFetchingEnvironment: DataFetchingEnvironment) {

        /**
         * Generates a [NodeQuery] which loads a single [Node]
         * Can use the `dataFetchingEnvironment` to fetch a subtree of node
         *
         * @param definition [NodeDefinition] of the node to load
         * @param context provides the sub-selection set, result path and arguments
         * @param additionalConditions list of conditions which are applied to filter the returned node
         * @param authorizationCondition optional authorization condition generated for the current query
         * @return the generated [NodeQuery] to load the node
         */
        internal fun generateOneNodeQuery(
            definition: NodeDefinition,
            context: FieldFetchingContext,
            additionalConditions: List<CypherConditionGenerator>,
            authorizationCondition: CypherConditionGenerator?
        ): NodeQuery {
            return generateOneNodeQuery(
                definition,
                context,
                additionalConditions + listOfNotNull(authorizationCondition),
            )
        }

        /**
         * Generates a [NodeQuery] which loads multiple [Node]s
         * Can use the `dataFetchingEnvironment` to fetch a subtree of node
         *
         * @param definition [NodeDefinition] of the nodes to load
         * @param context provides the sub-selection set, result path and arguments
         * @param additionalConditions list of conditions which are applied to filter the returned node
         * @param requiredPermission optional required permission
         * @param authorizationCondition optional authorization condition generated for the current query
         * @return the generated [NodeQuery] to load the node
         */
        internal fun generateManyNodeQuery(
            definition: NodeDefinition,
            context: FieldFetchingContext,
            additionalConditions: List<CypherConditionGenerator>,
            requiredPermission: Permission?,
            authorizationCondition: CypherConditionGenerator?
        ): NodeQuery {
            return generateManyNodeQuery(
                definition,
                context,
                additionalConditions + listOfNotNull(authorizationCondition),
                requiredPermission,
            )
        }

        /**
         * Generates a [SearchQuery] which loads multiple [Node]s
         *
         * @param definition [NodeDefinition] of the nodes to load
         * @param context provides the sub-selection set, result path and arguments
         * @param requiredPermission optional required permission
         * @param authorizationCondition optional authorization condition generated for the current query
         * @return the generated [SearchQuery] to load the nodes
         */
        internal fun generateSearchQuery(
            definition: NodeDefinition,
            context: FieldFetchingContext,
            requiredPermission: Permission?,
            authorizationCondition: CypherConditionGenerator?
        ): SearchQuery {
            return generateSearchQuery(
                definition,
                context,
                listOfNotNull(authorizationCondition),
                requiredPermission,
            )
        }

        /**
         * Generates query entries for all fields in [fields]
         *
         * @param fields fields to create subqueries based of
         * @param context provides the sub-selection set, result path and arguments
         * @return the generated query entries
         */
        private fun generateQueryEntries(
            fields: List<SelectedField>, context: FieldFetchingContext
        ): List<NodeQueryEntry<*>> {
            val entries = ArrayList<NodeQueryEntry<*>>()
            for (field in fields) {
                val onlyOnTypes = nodeDefinitionCollection.getNodeDefinitionsFromGraphQLNames(field.objectTypeNames)
                val firstPossibleType = onlyOnTypes.first()
                val fieldDefinition = firstPossibleType.getFieldDefinitionOrNull(field.name)
                if (fieldDefinition != null) {
                    entries.add(
                        fieldDefinition.createQueryEntry(
                            dataFetchingEnvironment, context.ofField(field), this@NodeQueryParser, onlyOnTypes
                        )
                    )
                }
            }
            return entries
        }

        /**
         * Generates a SubQuery based on a [RelationshipDefinition]
         *
         * @param fieldDefinition defines the generated subquery
         * @param relationshipDefinitions defines the relationship to load
         * @param context provides the sub-selection set, result path and arguments
         * @param requiredPermission optional authorization context for subqueries
         * @param onlyOnTypes if provided, types for which the subquery is active
         * @return the generated [NodeSubQuery]
         */
        internal fun generateSubQuery(
            fieldDefinition: FieldDefinition,
            relationshipDefinitions: List<AuthorizedRelationDefinition>,
            context: FieldFetchingContext,
            requiredPermission: Permission?,
            onlyOnTypes: List<NodeDefinition>?,
        ): NodeSubQuery {
            val nodeKClass = relationshipDefinitions.last().relationshipDefinition.nodeKClass
            val nodeDefinition = nodeDefinitionCollection.getNodeDefinition(nodeKClass)
            return when {
                relationshipDefinitions.size == 1 && relationshipDefinitions.single().relationshipDefinition is OneRelationshipDefinition -> {
                    NodeSubQuery(
                        fieldDefinition, generateOneNodeQuery(
                            nodeDefinition,
                            context,
                            emptyList(),
                        ), onlyOnTypes, relationshipDefinitions, context.resultKeyPath
                    )
                }

                else -> {
                    NodeSubQuery(
                        fieldDefinition, generateManyNodeQuery(
                            nodeDefinition,
                            context,
                            emptyList(),
                            requiredPermission,
                        ), onlyOnTypes, relationshipDefinitions, context.resultKeyPath
                    )
                }
            }
        }

        /**
         * Generates a [NodeQuery] which loads multiple [Node]s
         * Can use the `dataFetchingEnvironment` to fetch a subtree of node
         *
         * @param nodeDefinition definition of the nodes to load
         * @param context provides the sub-selection set, result path and arguments
         * @param additionalConditions list of conditions which are applied to filter the returned node
         * @param requiredPermission optional required permission
         * @return the generated [NodeQuery] to load the node
         */
        private fun generateManyNodeQuery(
            nodeDefinition: NodeDefinition,
            context: FieldFetchingContext,
            additionalConditions: List<CypherConditionGenerator>,
            requiredPermission: Permission?,
        ): NodeQuery {
            val filterDefinition = filterDefinitionCollection?.getFilterDefinition<Node>(nodeDefinition.nodeType)
            val filters = ArrayList(additionalConditions)
            val arguments = context.arguments
            val selectionSet = context.selectionSet
            arguments["filter"]?.also {
                if (filterDefinition == null) {
                    throw IllegalStateException("Cannot parse filter using only graphglue-core dependency")
                }
                filters.add(filterDefinition.parseFilter(it, requiredPermission))
            }
            val orderBy = arguments["orderBy"]?.let { parseOrder(it) } ?: IdOrder
            val subNodeQueryOptions = NodeQueryOptions(
                filters = filters,
                orderBy = orderBy,
                after = arguments["after"]?.let { orderBy.parseCursor(it as String, objectMapper) },
                before = arguments["before"]?.let { orderBy.parseCursor(it as String, objectMapper) },
                first = arguments["first"]?.let { (it as Int) + NODE_FETCH_OFFSET },
                last = arguments["last"]?.let { (it as Int) + NODE_FETCH_OFFSET },
                skip = arguments["skip"]?.let { it as Int },
                fetchTotalCount = selectionSet.contains("totalCount"),
                ignoreNodes = selectionSet.immediateFields?.singleOrNull()?.name == "totalCount"
            )
            val entries = mutableListOf<NodeQueryEntry<*>>()
            for (nodesField in selectionSet.getFields("nodes")) {
                val nodesContext = context.ofField(nodesField)
                entries += generateQueryEntries(nodesField.selectionSet.immediateFields, nodesContext)
            }
            for (edgesField in selectionSet.getFields("edges")) {
                val edgesContext = context.ofField(edgesField)
                for (nodeField in edgesField.selectionSet.getFields("node")) {
                    val nodeContext = edgesContext.ofField(nodeField)
                    entries += generateQueryEntries(nodeField.selectionSet.immediateFields, nodeContext)
                }
            }
            return NodeQuery(nodeDefinition, subNodeQueryOptions, entries)
        }

        /**
         * Generates a [SearchQuery] which loads multiple [Node]s
         *
         * @param nodeDefinition definition of the nodes to load
         * @param context provides the sub-selection set, result path and arguments
         * @param additionalConditions list of conditions which are applied to filter the returned node
         * @param requiredPermission optional required permission
         * @return the generated [SearchQuery] to load the node
         */
        private fun generateSearchQuery(
            nodeDefinition: NodeDefinition,
            context: FieldFetchingContext,
            additionalConditions: List<CypherConditionGenerator>,
            requiredPermission: Permission?,
        ): SearchQuery {
            val filterDefinition = filterDefinitionCollection?.getFilterDefinition<Node>(nodeDefinition.nodeType)
            val filters = ArrayList(additionalConditions)
            val arguments = context.arguments
            arguments["filter"]?.also {
                if (filterDefinition == null) {
                    throw IllegalStateException("Cannot parse filter using only graphglue-core dependency")
                }
                filters.add(filterDefinition.parseFilter(it, requiredPermission))
            }
            val queryOptions = SearchQueryOptions(
                filters = filters,
                query = arguments["query"] as String,
                first = arguments["first"] as Int,
                skip = arguments["skip"]?.let { it as Int },
            )
            val entries = generateQueryEntries(context.selectionSet.immediateFields, context)
            return SearchQuery(nodeDefinition, queryOptions, entries)
        }

        /**
         * Generates a [NodeQuery] which loads a single [Node]
         * Can use the `dataFetchingEnvironment` to fetch a subtree of node
         *
         * @param nodeDefinition definition of the node to load
         * @param context provides the sub-selection set, result path and arguments
         * @param additionalConditions list of conditions which are applied to filter the returned node, including
         *                             authorization condition
         * @return the generated [NodeQuery] to load the node
         */
        private fun generateOneNodeQuery(
            nodeDefinition: NodeDefinition,
            context: FieldFetchingContext,
            additionalConditions: List<CypherConditionGenerator>,
        ): NodeQuery {
            val subNodeQueryOptions = NodeQueryOptions(
                filters = additionalConditions, first = ONE_NODE_QUERY_LIMIT, fetchTotalCount = false, orderBy = null
            )
            val entries = generateQueryEntries(context.selectionSet.immediateFields, context)
            return NodeQuery(nodeDefinition, subNodeQueryOptions, entries)
        }
    }

    /**
     * Gets the authorization condition for the related nodes of a [RelationshipDefinition]
     *
     * @param requiredPermission optional required permission
     * @param relationshipDefinition defines the relationship
     * @return a condition generator which can be used as filter condition or null if `authorizationContext == null`
     */
    private fun getAuthorizationConditionWithRelationshipDefinition(
        requiredPermission: Permission?, relationshipDefinition: RelationshipDefinition
    ): CypherConditionGenerator? {
        return requiredPermission?.let {
            nodeDefinitionCollection.generateRelationshipAuthorizationCondition(
                relationshipDefinition, it
            )
        }
    }

    /**
     * Maps the provided [RelationshipDefinition]s to [AuthorizedRelationDefinition]s
     *
     * @param relationshipDefinitions the [RelationshipDefinition]s to transform
     * @param requiredPermission the permission which needs to be present on any node of the path
     * @return the mapped [RelationshipDefinition]s
     */
    private fun getAuthorizedRelationshipDefinitions(
        relationshipDefinitions: List<RelationshipDefinition>, requiredPermission: Permission?
    ): List<AuthorizedRelationDefinition> {
        return relationshipDefinitions.mapIndexed { index, relationshipDefinition ->
            val nodeKClass =
                relationshipDefinitions.getOrNull(index + 1)?.parentKClass ?: relationshipDefinition.nodeKClass
            AuthorizedRelationDefinition(
                relationshipDefinition,
                nodeDefinitionCollection.getNodeDefinition(nodeKClass),
                getAuthorizationConditionWithRelationshipDefinition(requiredPermission, relationshipDefinition)
            )
        }
    }

    /**
     * Gets the authorization condition for a [NodeDefinition] and an [Permission]
     *
     * @param requiredPermission optional required permission
     * @param nodeDefinition represents the [Node]s to get the authorization condition for
     * @return a condition generator which can be used as filter condition or null if `authorizationContext == null`
     */
    private fun getAuthorizationCondition(
        requiredPermission: Permission?, nodeDefinition: NodeDefinition
    ): CypherConditionGenerator? {
        return requiredPermission?.let {
            nodeDefinitionCollection.generateAuthorizationCondition(nodeDefinition, requiredPermission)
        }
    }
}
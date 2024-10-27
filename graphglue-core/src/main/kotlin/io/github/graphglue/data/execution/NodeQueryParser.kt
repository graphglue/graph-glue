package io.github.graphglue.data.execution

import com.fasterxml.jackson.databind.ObjectMapper
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingFieldSelectionSet
import graphql.schema.SelectedField
import io.github.graphglue.authorization.Permission
import io.github.graphglue.connection.filter.definition.FilterDefinition
import io.github.graphglue.connection.filter.definition.FilterDefinitionCollection
import io.github.graphglue.connection.order.IdOrder
import io.github.graphglue.connection.order.parseOrder
import io.github.graphglue.definition.*
import io.github.graphglue.model.Node
import io.github.graphglue.model.property.LazyLoadingSubqueryGenerator
import org.neo4j.cypherdsl.core.Cypher
import kotlin.reflect.full.isSubclassOf

/**
 * the id of the default part
 */
const val DEFAULT_PART_ID = "default"

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
     * RelationshipGraphQLNames for [NodeDefinition]s, but including names only defined in
     * subtypes
     */
    private val relationshipGraphQLNamesIncludingSubtypes = nodeDefinitionCollection.associateWith { definition ->
        nodeDefinitionCollection.filter { it.nodeType.isSubclassOf(definition.nodeType) }
            .flatMap { it.relationshipGraphQLNames }.toSet()
    }

    /**
     * ExtensionFieldGraphQLNames for [NodeDefinition]s, but including names only defined in
     * subtypes
     */
    private val extensionFieldGraphQLNamesIncludingSubTypes = nodeDefinitionCollection.associateWith { definition ->
        nodeDefinitionCollection.filter { it.nodeType.isSubclassOf(definition.nodeType) }
            .flatMap { it.extensionFieldGraphQLNames }.toSet()
    }

    /**
     * Generates a [NodeQuery] for a specific relationship
     * Can use the `dataFetchingEnvironment` to fetch a subtree of node
     *
     * @param definition the [NodeDefinition] of the related nodes to load
     * @param dataFetchingEnvironment provided to fetch a subtree of nodes
     * @param relationshipDefinition defines the relationship to load related nodes of
     * @param parentNode root [Node] of the relationship to load related nodes of
     * @param requiredPermission optional required permission
     * @return the generated [NodeQuery] to load related nodes of `rootNode`
     */
    fun generateRelationshipNodeQuery(
        definition: NodeDefinition,
        parentDefinition: NodeDefinition,
        dataFetchingEnvironment: DataFetchingEnvironment,
        relationshipDefinition: RelationshipDefinition,
        parentNode: Node,
        requiredPermission: Permission?
    ): NodeQuery {
        val additionalConditions = generateRelationshipCondition(
            parentDefinition, relationshipDefinition, parentNode
        )
        val authorizationCondition = getAuthorizationConditionWithRelationshipDefinition(
            requiredPermission, relationshipDefinition
        )
        val instance = NodeQueryParserInstance(dataFetchingEnvironment)
        return when (relationshipDefinition) {
            is OneRelationshipDefinition -> instance.generateOneNodeQuery(
                definition, additionalConditions, requiredPermission, authorizationCondition
            )

            is ManyRelationshipDefinition -> instance.generateManyNodeQuery(
                definition, additionalConditions, requiredPermission, authorizationCondition
            )

            else -> throw IllegalStateException("Unknown relationship type")
        }
    }

    /**
     * Generates a [NodeQuery] for a specific relationship
     * Can use the `loader` to fetch a subtree of node
     *
     * @param definition the [NodeDefinition] of the related nodes to load
     * @param loader used to fetch a subtree of nodes
     * @param relationshipDefinition defines the relationship to load related nodes of
     * @param parentNode root [Node] of the relationship to load related nodes of
     * @return the generated [NodeQuery] to load related nodes of `rootNode`
     */
    fun <T : Node?> generateRelationshipNodeQuery(
        definition: NodeDefinition,
        parentDefinition: NodeDefinition,
        loader: (LazyLoadingSubqueryGenerator<T>.() -> Unit)?,
        relationshipDefinition: RelationshipDefinition,
        parentNode: Node,
    ): NodeQuery {
        val additionalConditions = generateRelationshipCondition(
            parentDefinition, relationshipDefinition, parentNode
        )
        val parts = if (loader != null) {
            val generator = LazyLoadingSubqueryGenerator<T>(nodeDefinitionCollection)
            loader.invoke(generator)
            generator.toQueryParts()
        } else {
            emptyMap()
        }
        return NodeQuery(
            definition, NodeQueryOptions(
                filters = additionalConditions,
                orderBy = null,
                fetchTotalCount = false,
                first = if (relationshipDefinition is OneRelationshipDefinition) ONE_NODE_QUERY_LIMIT else null,
                overrideIsAllQuery = true
            ), parts
        )
    }

    /**
     * Generates the conditions to load only nodes of a relationship
     *
     * @param parentDefinition the [NodeDefinition] of the parent node
     * @param relationshipDefinition defines the relationship to load related nodes of
     * @param parentNode root [Node] of the relationship to load related nodes of
     * @return the generated conditions to load only nodes of the relationship
     */
    private fun generateRelationshipCondition(
        parentDefinition: NodeDefinition, relationshipDefinition: RelationshipDefinition, parentNode: Node
    ): List<CypherConditionGenerator> {
        val idParameter = Cypher.anonParameter(parentNode.rawId)
        val rootCypherNode = parentDefinition.node().named("a_related").withProperties(mapOf("id" to idParameter))
        return listOf(CypherConditionGenerator { node ->
            Cypher.any(Cypher.name("a_related_alias")).`in`(
                Cypher.listBasedOn(relationshipDefinition.generateRelationship(rootCypherNode, node))
                    .returning(rootCypherNode)
            ).where(Cypher.isTrue())
        })
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
            definition, additionalConditions, requiredPermission, authorizationCondition
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
            definition, additionalConditions, requiredPermission, authorizationCondition
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
            definition, requiredPermission, authorizationCondition
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
         * @param additionalConditions list of conditions which are applied to filter the returned node
         * @param requiredPermission optional required permission
         * @param authorizationCondition optional authorization condition generated for the current query
         * @return the generated [NodeQuery] to load the node
         */
        internal fun generateOneNodeQuery(
            definition: NodeDefinition,
            additionalConditions: List<CypherConditionGenerator>,
            requiredPermission: Permission?,
            authorizationCondition: CypherConditionGenerator?
        ): NodeQuery {
            return generateOneNodeQuery(
                definition,
                dataFetchingEnvironment.selectionSet,
                additionalConditions + listOfNotNull(authorizationCondition),
                requiredPermission
            )
        }

        /**
         * Generates a [NodeQuery] which loads multiple [Node]s
         * Can use the `dataFetchingEnvironment` to fetch a subtree of node
         *
         * @param definition [NodeDefinition] of the nodes to load
         * @param additionalConditions list of conditions which are applied to filter the returned node
         * @param requiredPermission optional required permission
         * @param authorizationCondition optional authorization condition generated for the current query
         * @return the generated [NodeQuery] to load the node
         */
        internal fun generateManyNodeQuery(
            definition: NodeDefinition,
            additionalConditions: List<CypherConditionGenerator>,
            requiredPermission: Permission?,
            authorizationCondition: CypherConditionGenerator?
        ): NodeQuery {
            return generateManyNodeQuery(
                definition,
                dataFetchingEnvironment.selectionSet,
                dataFetchingEnvironment.arguments,
                additionalConditions + listOfNotNull(authorizationCondition),
                requiredPermission,
            )
        }

        /**
         * Generates a [SearchQuery] which loads multiple [Node]s
         *
         * @param definition [NodeDefinition] of the nodes to load
         * @param requiredPermission optional required permission
         * @param authorizationCondition optional authorization condition generated for the current query
         * @return the generated [SearchQuery] to load the nodes
         */
        internal fun generateSearchQuery(
            definition: NodeDefinition,
            requiredPermission: Permission?,
            authorizationCondition: CypherConditionGenerator?
        ): SearchQuery {
            return generateSearchQuery(
                definition,
                dataFetchingEnvironment.selectionSet,
                dataFetchingEnvironment.arguments,
                listOfNotNull(authorizationCondition),
                requiredPermission,
            )
        }

        /**
         * Generates a NodeQuery for a [NodeDefinition]
         * Creates subqueries for the provided `fieldParts`
         *
         * @param definition definition for the [Node] to load
         * @param fieldParts parts which are used to create subqueries
         * @param nodeQueryOptions options for this [NodeQuery]
         * @param requiredPermission authorization context for subqueries
         */
        private fun generateNodeQuery(
            definition: NodeDefinition,
            fieldParts: Map<String, List<SelectedField>>,
            nodeQueryOptions: NodeQueryOptions,
            requiredPermission: Permission?,
        ): NodeQuery {
            val parts = generateQueryParts(definition, fieldParts, requiredPermission)
            return NodeQuery(definition, nodeQueryOptions, parts)
        }

        /**
         * Generates query parts for all fields in `fieldParts`
         *
         * @param definition definition for the [Node] to load
         * @param fieldParts parts which are used to create subqueries
         * @param requiredPermission authorization context for subqueries
         * @return the generated query parts
         */
        private fun generateQueryParts(
            definition: NodeDefinition, fieldParts: Map<String, List<SelectedField>>, requiredPermission: Permission?
        ): Map<String, NodeQueryPart> {
            val relationshipGraphQLNames = relationshipGraphQLNamesIncludingSubtypes[definition]!!
            val extensionFieldGraphQLNames = extensionFieldGraphQLNamesIncludingSubTypes[definition]!!
            val parts = fieldParts.mapValues { (_, fields) ->
                val subQueries = mutableListOf<NodeSubQuery>()
                val extensionFields = mutableListOf<NodeExtensionField>()
                for (field in fields) {
                    when (field.fieldDefinitions.first().name) {
                        in relationshipGraphQLNames -> generateSubQueryForField(
                            field, requiredPermission
                        )?.let { subQueries.add(it) }

                        in extensionFieldGraphQLNames -> {
                            generateExtensionFieldForField(
                                field,
                            )?.let { extensionFields.add(it) }
                        }

                    }
                }
                NodeQueryPart(subQueries, extensionFields)
            }
            return parts
        }

        /**
         * Generates a [NodeSubQuery] for a [SelectedField] if possible.
         * If the provided field is no relationship field, no [NodeSubQuery] is generated
         *
         * @param field the [SelectedField] to generate the [NodeSubQuery] for, may not be a relationship field
         * @param requiredPermission authorization context for subqueries
         * @return the subquery if generated
         */
        private fun generateSubQueryForField(
            field: SelectedField, requiredPermission: Permission?
        ): NodeSubQuery? {
            val onlyOnTypes = nodeDefinitionCollection.getNodeDefinitionsFromGraphQLNames(field.objectTypeNames)
            val firstPossibleType = onlyOnTypes.first()
            return firstPossibleType.relationshipDefinitions[field.name]?.let { relationshipDefinition ->
                val authorizationCondition = getAuthorizationConditionWithRelationshipDefinition(
                    requiredPermission, relationshipDefinition
                )
                val subQuery = generateSubQuery(
                    relationshipDefinition, field, authorizationCondition, requiredPermission, onlyOnTypes
                )
                subQuery
            }
        }

        private fun generateExtensionFieldForField(
            field: SelectedField
        ): NodeExtensionField? {
            val onlyOnTypes = nodeDefinitionCollection.getNodeDefinitionsFromGraphQLNames(field.objectTypeNames)
            val firstPossibleType = onlyOnTypes.first()
            return firstPossibleType.extensionFieldDefinitions[field.name]?.let { extensionFieldDefinition ->
                NodeExtensionField(
                    extensionFieldDefinition, dataFetchingEnvironment, field, onlyOnTypes, field.resultKey
                )
            }
        }

        /**
         * Generates a SubQuery based on a [RelationshipDefinition]
         *
         * @param relationshipDefinition defines the relationship to load
         * @param field defines which nodes to load from the relationship
         * @param authorizationCondition optional additional condition for authorization
         * @param requiredPermission optional authorization context for subqueries
         * @param onlyOnTypes types for which the subquery is active
         * @return the generated [NodeSubQuery]
         */
        private fun generateSubQuery(
            relationshipDefinition: RelationshipDefinition,
            field: SelectedField,
            authorizationCondition: CypherConditionGenerator?,
            requiredPermission: Permission?,
            onlyOnTypes: List<NodeDefinition>,
        ) = when (relationshipDefinition) {
            is ManyRelationshipDefinition -> {
                NodeSubQuery(
                    generateManyNodeQuery(
                        nodeDefinitionCollection.getNodeDefinition(relationshipDefinition.nodeKClass),
                        field.selectionSet,
                        field.arguments,
                        listOfNotNull(authorizationCondition),
                        requiredPermission,
                    ), onlyOnTypes, relationshipDefinition, field.resultKey
                )
            }

            is OneRelationshipDefinition -> {
                NodeSubQuery(
                    generateOneNodeQuery(
                        nodeDefinitionCollection.getNodeDefinition(relationshipDefinition.nodeKClass),
                        field.selectionSet,
                        listOfNotNull(authorizationCondition),
                        requiredPermission,
                    ), onlyOnTypes, relationshipDefinition, field.resultKey
                )
            }

            else -> throw IllegalStateException("unknown RelationshipDefinition type")
        }

        /**
         * Generates a [NodeQuery] which loads multiple [Node]s
         * Can use the `dataFetchingEnvironment` to fetch a subtree of node
         *
         * @param nodeDefinition definition of the nodes to load
         * @param selectionSet optional, used to generate subqueries
         * @param arguments used to get pagination arguments
         * @param additionalConditions list of conditions which are applied to filter the returned node
         * @param requiredPermission optional required permission
         * @return the generated [NodeQuery] to load the node
         */
        private fun generateManyNodeQuery(
            nodeDefinition: NodeDefinition,
            selectionSet: DataFetchingFieldSelectionSet,
            arguments: Map<String, Any>,
            additionalConditions: List<CypherConditionGenerator>,
            requiredPermission: Permission?,
        ): NodeQuery {
            val filterDefinition = filterDefinitionCollection?.getFilterDefinition<Node>(nodeDefinition.nodeType)
            val filters = ArrayList(additionalConditions)
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
            val parts = HashMap<String, List<SelectedField>>()
            val nodesParts = selectionSet.getFields("nodes")
            for (nodesPart in nodesParts) {
                parts[nodesPart.resultKey] = nodesPart.selectionSet.immediateFields
            }
            for (edgesPart in selectionSet.getFields("edges")) {
                for (nodePart in edgesPart.selectionSet.getFields("node")) {
                    parts["${edgesPart.resultKey}/${nodePart.resultKey}"] = nodePart.selectionSet.immediateFields
                }
            }
            return generateNodeQuery(
                nodeDefinition, parts, subNodeQueryOptions, requiredPermission
            )
        }

        /**
         * Generates a [SearchQuery] which loads multiple [Node]s
         *
         * @param nodeDefinition definition of the nodes to load
         * @param selectionSet optional, used to generate subqueries
         * @param arguments used to get pagination arguments
         * @param additionalConditions list of conditions which are applied to filter the returned node
         * @param requiredPermission optional required permission
         * @return the generated [SearchQuery] to load the node
         */
        fun generateSearchQuery(
            nodeDefinition: NodeDefinition,
            selectionSet: DataFetchingFieldSelectionSet,
            arguments: Map<String, Any>,
            additionalConditions: List<CypherConditionGenerator>,
            requiredPermission: Permission?,
        ): SearchQuery {
            val filterDefinition = filterDefinitionCollection?.getFilterDefinition<Node>(nodeDefinition.nodeType)
            val filters = ArrayList(additionalConditions)
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
            val queryParts = generateQueryParts(
                nodeDefinition, mapOf(DEFAULT_PART_ID to (selectionSet.immediateFields)), requiredPermission
            )
            return SearchQuery(nodeDefinition, queryOptions, queryParts)
        }

        /**
         * Generates a [NodeQuery] which loads a single [Node]
         * Can use the `dataFetchingEnvironment` to fetch a subtree of node
         *
         * @param nodeDefinition definition of the node to load
         * @param selectionSet optional, used to generate subqueries
         * @param additionalConditions list of conditions which are applied to filter the returned node, including
         *                             authorization condition
         * @param requiredPermission optional required permission
         * @return the generated [NodeQuery] to load the node
         */
        private fun generateOneNodeQuery(
            nodeDefinition: NodeDefinition,
            selectionSet: DataFetchingFieldSelectionSet,
            additionalConditions: List<CypherConditionGenerator>,
            requiredPermission: Permission?,
        ): NodeQuery {
            val subNodeQueryOptions = NodeQueryOptions(
                filters = additionalConditions, first = ONE_NODE_QUERY_LIMIT, fetchTotalCount = false, orderBy = null
            )
            return generateNodeQuery(
                nodeDefinition,
                mapOf(DEFAULT_PART_ID to (selectionSet.immediateFields)),
                subNodeQueryOptions,
                requiredPermission,
            )
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

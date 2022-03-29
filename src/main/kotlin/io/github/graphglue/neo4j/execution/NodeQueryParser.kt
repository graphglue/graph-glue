package io.github.graphglue.neo4j.execution

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.graphglue.graphql.connection.filter.definition.FilterDefinition
import io.github.graphglue.graphql.connection.filter.definition.FilterDefinitionCollection
import io.github.graphglue.graphql.connection.order.IdOrder
import io.github.graphglue.graphql.connection.order.parseOrder
import io.github.graphglue.model.NODE_RELATIONSHIP_DIRECTIVE
import io.github.graphglue.model.Node
import io.github.graphglue.neo4j.CypherConditionGenerator
import io.github.graphglue.neo4j.authorization.AuthorizationContext
import io.github.graphglue.neo4j.execution.definition.*
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingFieldSelectionSet
import graphql.schema.SelectedField
import org.neo4j.cypherdsl.core.Conditions
import org.neo4j.cypherdsl.core.Cypher
import org.neo4j.cypherdsl.core.Predicates

/**
 * the id of the default part
 */
const val DEFAULT_PART_ID = "default"

/**
 * Parser to get [NodeQuery]s
 * Can be used to create queries which load a subtree of nodes in one query
 *
 * @param nodeDefinitionCollection used to get the [NodeDefinition] for a specific [Node]
 * @param filterDefinitionCollection used to get the [FilterDefinition] for a specific [Node]
 * @param objectMapper used to parse cursors
 */
class NodeQueryParser(
    val nodeDefinitionCollection: NodeDefinitionCollection,
    val filterDefinitionCollection: FilterDefinitionCollection,
    val objectMapper: ObjectMapper
) {

    /**
     * Generates a [NodeQuery] for a specific relationship
     * Can use the `dataFetchingEnvironment` to fetch a subtree of node
     *
     * @param definition the [NodeDefinition] of the related nodes to load
     * @param dataFetchingEnvironment can optionally be provided to fetch a subtree of nodes
     * @param relationshipDefinition defines the relationship to load related nodes of
     * @param parentNode root [Node] of the relationship to load related nodes of
     * @param authorizationContext optional context to add authorization filter conditions
     * @return the generated [NodeQuery] to load related nodes of `rootNode`
     */
    fun generateRelationshipNodeQuery(
        definition: NodeDefinition,
        dataFetchingEnvironment: DataFetchingEnvironment?,
        relationshipDefinition: RelationshipDefinition,
        parentNode: Node,
        authorizationContext: AuthorizationContext?
    ): NodeQuery {
        val idParameter = Cypher.anonParameter(parentNode.rawId)
        val rootCypherNode = Cypher.anyNode().withProperties(mapOf("id" to idParameter))
        val additionalConditions = listOf(CypherConditionGenerator { node ->
            Predicates.any(rootCypherNode.requiredSymbolicName).`in`(
                Cypher.listBasedOn(relationshipDefinition.generateRelationship(rootCypherNode, node))
                    .returning(rootCypherNode)
            ).where(Conditions.isTrue())
        })
        val authorizationCondition = getAuthorizationConditionWithRelationshipDefinition(
            authorizationContext, relationshipDefinition
        )

        return when (relationshipDefinition) {
            is OneRelationshipDefinition -> generateOneNodeQuery(
                definition, dataFetchingEnvironment, additionalConditions, authorizationContext, authorizationCondition
            )
            is ManyRelationshipDefinition -> generateManyNodeQuery(
                definition, dataFetchingEnvironment, additionalConditions, authorizationContext, authorizationCondition
            )
            else -> throw IllegalStateException("Unknown relationship type")
        }
    }

    /**
     * Generates a [NodeQuery] which loads a single [Node]
     * Can use the `dataFetchingEnvironment` to fetch a subtree of node
     *
     * @param definition [NodeDefinition] of the node to load
     * @param dataFetchingEnvironment can optionally be provided to fetch a subtree of nodes
     * @param additionalConditions list of conditions which are applied to filter the returned node
     * @param authorizationContext optional context to add authorization filter conditions
     * @return the generated [NodeQuery] to load the node
     */
    fun generateOneNodeQuery(
        definition: NodeDefinition,
        dataFetchingEnvironment: DataFetchingEnvironment?,
        additionalConditions: List<CypherConditionGenerator>,
        authorizationContext: AuthorizationContext?
    ): NodeQuery {
        val authorizationCondition = getAuthorizationCondition(authorizationContext, definition)
        return generateOneNodeQuery(
            definition,
            dataFetchingEnvironment,
            additionalConditions,
            authorizationContext,
            authorizationCondition
        )
    }

    /**
     * Generates a [NodeQuery] which loads multiple [Node]s
     * Can use the `dataFetchingEnvironment` to fetch a subtree of node
     *
     * @param definition [NodeDefinition] of the nodes to load
     * @param dataFetchingEnvironment can optionally be provided to fetch a subtree of nodes
     * @param additionalConditions list of conditions which are applied to filter the returned node
     * @param authorizationContext optional context to add authorization filter conditions
     * @return the generated [NodeQuery] to load the node
     */
    fun generateManyNodeQuery(
        definition: NodeDefinition,
        dataFetchingEnvironment: DataFetchingEnvironment?,
        additionalConditions: List<CypherConditionGenerator>,
        authorizationContext: AuthorizationContext?
    ): NodeQuery {
        val authorizationCondition = getAuthorizationCondition(authorizationContext, definition)
        return generateManyNodeQuery(
            definition,
            dataFetchingEnvironment,
            additionalConditions,
            authorizationContext,
            authorizationCondition
        )
    }

    /**
     * Generates a [NodeQuery] which loads a single [Node]
     * Can use the `dataFetchingEnvironment` to fetch a subtree of node
     *
     * @param definition [NodeDefinition] of the node to load
     * @param dataFetchingEnvironment can optionally be provided to fetch a subtree of nodes
     * @param additionalConditions list of conditions which are applied to filter the returned node
     * @param authorizationContext optional context to add authorization filter conditions
     * @param authorizationCondition optional authorization condition generated for the current query
     * @return the generated [NodeQuery] to load the node
     */
    private fun generateOneNodeQuery(
        definition: NodeDefinition,
        dataFetchingEnvironment: DataFetchingEnvironment?,
        additionalConditions: List<CypherConditionGenerator>,
        authorizationContext: AuthorizationContext?,
        authorizationCondition: CypherConditionGenerator?
    ): NodeQuery {
        return generateOneNodeQuery(
            definition,
            dataFetchingEnvironment?.selectionSet,
            additionalConditions + listOfNotNull(authorizationCondition),
            authorizationContext
        )
    }

    /**
     * Generates a [NodeQuery] which loads multiple [Node]s
     * Can use the `dataFetchingEnvironment` to fetch a subtree of node
     *
     * @param definition [NodeDefinition] of the nodes to load
     * @param dataFetchingEnvironment can optionally be provided to fetch a subtree of nodes
     * @param additionalConditions list of conditions which are applied to filter the returned node
     * @param authorizationContext optional context to add authorization filter conditions
     * @param authorizationCondition optional authorization condition generated for the current query
     * @return the generated [NodeQuery] to load the node
     */
    private fun generateManyNodeQuery(
        definition: NodeDefinition,
        dataFetchingEnvironment: DataFetchingEnvironment?,
        additionalConditions: List<CypherConditionGenerator>,
        authorizationContext: AuthorizationContext?,
        authorizationCondition: CypherConditionGenerator?
    ): NodeQuery {
        return generateManyNodeQuery(
            definition,
            dataFetchingEnvironment?.selectionSet,
            dataFetchingEnvironment?.arguments ?: emptyMap(),
            additionalConditions + listOfNotNull(authorizationCondition),
            authorizationContext
        )
    }

    /**
     * Generates a NodeQuery for a [NodeDefinition]
     * Creates subqueries for the provided `fieldParts`
     *
     * @param definition definition for the [Node] to load
     * @param fieldParts parts which are used to create subqueries
     * @param nodeQueryOptions options for this [NodeQuery]
     * @param authorizationContext authorization context for subqueries
     */
    private fun generateNodeQuery(
        definition: NodeDefinition,
        fieldParts: Map<String, List<SelectedField>>,
        nodeQueryOptions: NodeQueryOptions,
        authorizationContext: AuthorizationContext?
    ): NodeQuery {
        val subQueries = ArrayList<NodeSubQuery>()
        val parts = fieldParts.mapValues {
            val (_, fields) = it
            for (field in fields) {
                if (field.fieldDefinitions.first().hasDirective(NODE_RELATIONSHIP_DIRECTIVE)) {
                    val onlyOnTypes = nodeDefinitionCollection.getNodeDefinitionsFromGraphQLNames(field.objectTypeNames)
                    val firstPossibleType = onlyOnTypes.first()
                    val relationshipDefinition = firstPossibleType.relationshipDefinitions[field.name]!!
                    val authorizationCondition = getAuthorizationConditionWithRelationshipDefinition(
                        authorizationContext,
                        relationshipDefinition
                    )
                    val subQuery = generateSubQuery(
                        relationshipDefinition,
                        field,
                        authorizationCondition,
                        authorizationContext,
                        onlyOnTypes
                    )
                    subQueries.add(subQuery)
                }
            }
            NodeQueryPart(subQueries)
        }
        return NodeQuery(definition, nodeQueryOptions, parts)
    }

    /**
     * Generates a SubQuery based on a [RelationshipDefinition]
     *
     * @param relationshipDefinition defines the relationship to load
     * @param field defines which nodes to load from the relationship
     * @param authorizationCondition optional additional condition for authorization
     * @param authorizationContext optional authorization context for subqueries
     * @param onlyOnTypes types for which the subquery is active
     * @return the generated [NodeSubQuery]
     */
    private fun generateSubQuery(
        relationshipDefinition: RelationshipDefinition,
        field: SelectedField,
        authorizationCondition: CypherConditionGenerator?,
        authorizationContext: AuthorizationContext?,
        onlyOnTypes: List<NodeDefinition>
    ) = when (relationshipDefinition) {
        is ManyRelationshipDefinition -> {
            NodeSubQuery(
                generateManyNodeQuery(
                    nodeDefinitionCollection.getNodeDefinition(relationshipDefinition.nodeKClass),
                    field.selectionSet,
                    field.arguments,
                    listOfNotNull(authorizationCondition),
                    authorizationContext
                ), onlyOnTypes, relationshipDefinition, field.resultKey
            )
        }
        is OneRelationshipDefinition -> {
            NodeSubQuery(
                generateOneNodeQuery(
                    nodeDefinitionCollection.getNodeDefinition(relationshipDefinition.nodeKClass),
                    field.selectionSet,
                    listOfNotNull(authorizationCondition),
                    authorizationContext
                ), onlyOnTypes, relationshipDefinition, field.resultKey
            )
        }
        else -> throw IllegalStateException("unknown RelationshipDefinition type")
    }

    /**
     * Gets the authorization condition for the related nodes of a [RelationshipDefinition]
     *
     * @param authorizationContext optional context used to get authorization name and parameters
     * @param relationshipDefinition defines the relationship
     * @return a condition generator which can be used as filter condition or null if `authorizationContext == null`
     */
    private fun getAuthorizationConditionWithRelationshipDefinition(
        authorizationContext: AuthorizationContext?,
        relationshipDefinition: RelationshipDefinition
    ): CypherConditionGenerator? {
        return authorizationContext?.let {
            nodeDefinitionCollection.generateRelationshipAuthorizationCondition(
                relationshipDefinition, it
            )
        }
    }

    /**
     * Gets the authorization condition for a [NodeDefinition] and an [AuthorizationContext]
     *
     * @param authorizationContext optional context used to get authorization name and parameters
     * @param nodeDefinition represents the [Node]s to get the authorization condition for
     * @return a condition generator which can be used as filter condition or null if `authorizationContext == null`
     */
    private fun getAuthorizationCondition(
        authorizationContext: AuthorizationContext?,
        nodeDefinition: NodeDefinition
    ): CypherConditionGenerator? {
        return authorizationContext?.let {
            nodeDefinitionCollection.generateAuthorizationCondition(nodeDefinition, authorizationContext)
        }
    }

    /**
     * Generates a [NodeQuery] which loads multiple [Node]s
     * Can use the `dataFetchingEnvironment` to fetch a subtree of node
     *
     * @param nodeDefinition definition of the nodes to load
     * @param selectionSet optional, used to generate subqueries
     * @param arguments used to get pagination arguments
     * @param additionalConditions list of conditions which are applied to filter the returned node
     * @param authorizationContext optional context to add authorization filter conditions,
     *                             including authorization condition
     * @return the generated [NodeQuery] to load the node
     */
    private fun generateManyNodeQuery(
        nodeDefinition: NodeDefinition,
        selectionSet: DataFetchingFieldSelectionSet?,
        arguments: Map<String, Any>,
        additionalConditions: List<CypherConditionGenerator>,
        authorizationContext: AuthorizationContext?
    ): NodeQuery {
        val filterDefinition = filterDefinitionCollection.getFilterDefinition<Node>(nodeDefinition.nodeType)
        val filters = ArrayList(additionalConditions)
        arguments["filter"]?.also {
            filters.add(filterDefinition.parseFilter(it))
        }
        val orderBy = arguments["orderBy"]?.let { parseOrder(it) } ?: IdOrder
        val subNodeQueryOptions = NodeQueryOptions(
            filters = filters,
            orderBy = orderBy,
            after = arguments["after"]?.let { orderBy.parseCursor(it as String, objectMapper) },
            before = arguments["before"]?.let { orderBy.parseCursor(it as String, objectMapper) },
            first = arguments["first"]?.let { (it as Int) + 1 },
            last = arguments["last"]?.let { (it as Int) + 1 },
            fetchTotalCount = selectionSet?.contains("totalCount") ?: true
        )
        val parts = HashMap<String, List<SelectedField>>()
        val nodesParts = selectionSet?.getFields("nodes") ?: emptyList()
        for (nodesPart in nodesParts) {
            parts[nodesPart.resultKey] = nodesPart.selectionSet.immediateFields
        }
        for (edgesPart in selectionSet?.getFields("edges") ?: emptyList()) {
            for (nodePart in edgesPart.selectionSet.getFields("node")) {
                parts["${edgesPart.resultKey}/${nodePart.resultKey}"] = nodePart.selectionSet.immediateFields
            }
        }
        return generateNodeQuery(
            nodeDefinition, parts, subNodeQueryOptions, authorizationContext
        )
    }

    /**
     * Generates a [NodeQuery] which loads a single [Node]
     * Can use the `dataFetchingEnvironment` to fetch a subtree of node
     *
     * @param nodeDefinition definition of the node to load
     * @param selectionSet optional, used to generate subqueries
     * @param additionalConditions list of conditions which are applied to filter the returned node, including
     *                             authorization condition
     * @param authorizationContext optional context to add authorization filter conditions
     * @return the generated [NodeQuery] to load the node
     */
    private fun generateOneNodeQuery(
        nodeDefinition: NodeDefinition,
        selectionSet: DataFetchingFieldSelectionSet?,
        additionalConditions: List<CypherConditionGenerator>,
        authorizationContext: AuthorizationContext?
    ): NodeQuery {
        val subNodeQueryOptions = NodeQueryOptions(filters = additionalConditions, first = 1, fetchTotalCount = false)
        return generateNodeQuery(
            nodeDefinition,
            mapOf(DEFAULT_PART_ID to (selectionSet?.immediateFields ?: emptyList())),
            subNodeQueryOptions,
            authorizationContext
        )
    }
}

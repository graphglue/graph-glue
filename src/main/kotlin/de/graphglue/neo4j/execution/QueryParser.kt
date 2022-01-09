package de.graphglue.neo4j.execution

import com.fasterxml.jackson.databind.ObjectMapper
import de.graphglue.graphql.connection.filter.definition.FilterDefinitionCollection
import de.graphglue.graphql.connection.order.IdOrder
import de.graphglue.graphql.connection.order.parseOrder
import de.graphglue.model.NODE_RELATIONSHIP_DIRECTIVE
import de.graphglue.model.Node
import de.graphglue.neo4j.CypherConditionGenerator
import de.graphglue.neo4j.authorization.AuthorizationContext
import de.graphglue.neo4j.execution.definition.*
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingFieldSelectionSet
import graphql.schema.SelectedField
import org.neo4j.cypherdsl.core.Conditions
import org.neo4j.cypherdsl.core.Cypher
import org.neo4j.cypherdsl.core.Predicates

const val DEFAULT_PART_ID = "default"

class QueryParser(
    val nodeDefinitionCollection: NodeDefinitionCollection,
    val filterDefinitionCollection: FilterDefinitionCollection,
    val objectMapper: ObjectMapper
) {

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

    private fun generateNodeQuery(
        definition: NodeDefinition,
        fieldParts: Map<String, List<SelectedField>>,
        queryOptions: QueryOptions,
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
        return NodeQuery(definition, queryOptions, parts)
    }

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

    private fun getAuthorizationCondition(
        authorizationContext: AuthorizationContext?,
        nodeDefinition: NodeDefinition
    ): CypherConditionGenerator? {
        return authorizationContext?.let {
            nodeDefinitionCollection.generateAuthorizationCondition(nodeDefinition, authorizationContext)
        }
    }

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
        val subQueryOptions = QueryOptions(
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
            nodeDefinition, parts, subQueryOptions, authorizationContext
        )
    }

    private fun generateOneNodeQuery(
        nodeDefinition: NodeDefinition,
        selectionSet: DataFetchingFieldSelectionSet?,
        additionalConditions: List<CypherConditionGenerator>,
        authorizationContext: AuthorizationContext?
    ): NodeQuery {
        val subQueryOptions = QueryOptions(filters = additionalConditions, first = 1, fetchTotalCount = false)
        return generateNodeQuery(
            nodeDefinition,
            mapOf(DEFAULT_PART_ID to (selectionSet?.immediateFields ?: emptyList())),
            subQueryOptions,
            authorizationContext
        )
    }
}

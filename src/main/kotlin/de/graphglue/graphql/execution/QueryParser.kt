package de.graphglue.graphql.execution

import com.fasterxml.jackson.databind.ObjectMapper
import de.graphglue.graphql.connection.filter.definition.FilterDefinitionCollection
import de.graphglue.graphql.connection.order.IdOrder
import de.graphglue.graphql.connection.order.parseOrder
import de.graphglue.graphql.execution.definition.*
import de.graphglue.model.NODE_RELATIONSHIP_DIRECTIVE
import de.graphglue.model.Node
import de.graphglue.neo4j.CypherConditionGenerator
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingFieldSelectionSet
import graphql.schema.SelectedField
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
        parentNode: Node
    ): NodeQuery {
        val rootCypherNode = Cypher.anyNode().withProperties(mapOf("id" to parentNode.rawId))
        val relationshipCondition = CypherConditionGenerator { node ->
            Predicates.exists(
                relationshipDefinition.generateRelationship(
                    rootCypherNode,
                    node
                )
            )
        }
        return when (relationshipDefinition) {
            is OneRelationshipDefinition -> generateOneNodeQuery(
                definition, dataFetchingEnvironment, listOf(relationshipCondition)
            )
            is ManyRelationshipDefinition -> generateManyNodeQuery(
                definition, dataFetchingEnvironment, listOf(relationshipCondition)
            )
            else -> throw IllegalStateException("Unknown relationship type")
        }
    }

    fun generateOneNodeQuery(
        definition: NodeDefinition,
        dataFetchingEnvironment: DataFetchingEnvironment?,
        additionalConditions: List<CypherConditionGenerator>
    ): NodeQuery {
        return generateOneNodeQuery(definition, dataFetchingEnvironment?.selectionSet, additionalConditions)
    }

    fun generateManyNodeQuery(
        definition: NodeDefinition,
        dataFetchingEnvironment: DataFetchingEnvironment?,
        additionalConditions: List<CypherConditionGenerator>
    ): NodeQuery {
        return generateManyNodeQuery(
            definition,
            dataFetchingEnvironment?.selectionSet,
            dataFetchingEnvironment?.arguments ?: emptyMap(),
            additionalConditions
        )
    }

    private fun generateNodeQuery(
        definition: NodeDefinition, fieldParts: Map<String, List<SelectedField>>, queryOptions: QueryOptions
    ): NodeQuery {
        val oneSubQueries = ArrayList<NodeSubQuery>()
        val manySubQueries = ArrayList<NodeSubQuery>()
        val parts = fieldParts.mapValues {
            val (_, fields) = it
            for (field in fields) {
                if (field.fieldDefinitions.first().hasDirective(NODE_RELATIONSHIP_DIRECTIVE)) {
                    val onlyOnTypes = nodeDefinitionCollection.getNodeDefinitionsFromGraphQLNames(field.objectTypeNames)
                    val firstPossibleType = onlyOnTypes.first()
                    val manyRelationshipDefinition = firstPossibleType.manyRelationshipDefinitions[field.name]
                    if (manyRelationshipDefinition != null) {
                        val subQuery = NodeSubQuery(
                            generateManyNodeQuery(
                                nodeDefinitionCollection.getNodeDefinition(manyRelationshipDefinition.nodeKClass),
                                field.selectionSet,
                                field.arguments
                            ),
                            onlyOnTypes,
                            manyRelationshipDefinition,
                            field.resultKey
                        )
                        manySubQueries.add(subQuery)
                    } else {
                        val oneRelationshipDefinition = firstPossibleType.oneRelationshipDefinitions[field.name]!!
                        val subQuery = NodeSubQuery(
                            generateOneNodeQuery(
                                nodeDefinitionCollection.getNodeDefinition(oneRelationshipDefinition.nodeKClass),
                                field.selectionSet
                            ),
                            onlyOnTypes,
                            oneRelationshipDefinition,
                            field.resultKey
                        )
                        oneSubQueries.add(subQuery)
                    }
                }
            }
            NodeQueryPart(oneSubQueries + manySubQueries)
        }
        return NodeQuery(definition, queryOptions, parts)
    }

    private fun generateManyNodeQuery(
        nodeDefinition: NodeDefinition,
        selectionSet: DataFetchingFieldSelectionSet?,
        arguments: Map<String, Any>,
        additionalConditions: List<CypherConditionGenerator> = emptyList()
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
        for (nodesPart in selectionSet?.getFields("nodes") ?: emptyList()) {
            parts[nodesPart.resultKey] = nodesPart.selectionSet.fields
        }
        for (edgesPart in selectionSet?.getFields("edges") ?: emptyList()) {
            for (nodePart in edgesPart.selectionSet.getFields("node")) {
                parts["${edgesPart.resultKey}/${nodePart.resultKey}"] = nodePart.selectionSet.fields
            }
        }
        return generateNodeQuery(
            nodeDefinition, parts, subQueryOptions
        )
    }

    private fun generateOneNodeQuery(
        nodeDefinition: NodeDefinition,
        selectionSet: DataFetchingFieldSelectionSet?,
        additionalConditions: List<CypherConditionGenerator> = emptyList()
    ): NodeQuery {
        val subQueryOptions = QueryOptions(filters = additionalConditions, first = 1, fetchTotalCount = false)
        return generateNodeQuery(
            nodeDefinition, mapOf(DEFAULT_PART_ID to (selectionSet?.fields ?: emptyList())), subQueryOptions
        )
    }
}

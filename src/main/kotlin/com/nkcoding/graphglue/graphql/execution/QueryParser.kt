package com.nkcoding.graphglue.graphql.execution

import com.fasterxml.jackson.databind.ObjectMapper
import com.nkcoding.graphglue.graphql.connection.filter.definition.FilterDefinitionCollection
import com.nkcoding.graphglue.graphql.connection.order.IdOrderField
import com.nkcoding.graphglue.graphql.connection.order.Order
import com.nkcoding.graphglue.graphql.connection.order.OrderDirection
import com.nkcoding.graphglue.graphql.connection.order.parseOrder
import com.nkcoding.graphglue.graphql.execution.definition.ManyRelationshipDefinition
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinition
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinitionCollection
import com.nkcoding.graphglue.graphql.execution.definition.OneRelationshipDefinition
import com.nkcoding.graphglue.model.NODE_RELATIONSHIP_DIRECTIVE
import graphql.schema.DataFetchingEnvironment
import graphql.schema.SelectedField

class QueryParser(
    val nodeDefinitionCollection: NodeDefinitionCollection,
    val filterDefinitionCollection: FilterDefinitionCollection,
    val objectMapper: ObjectMapper
) {
    fun generateNodeQuery(
        definition: NodeDefinition, dataFetchingEnvironment: DataFetchingEnvironment, queryOptions: QueryOptions
    ): NodeQuery {
        return generateNodeQuery(
            definition, mapOf("default" to dataFetchingEnvironment.selectionSet.fields), queryOptions
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
                            generateManyNodeQuery(manyRelationshipDefinition, field),
                            onlyOnTypes,
                            manyRelationshipDefinition,
                            field.resultKey
                        )
                        manySubQueries.add(subQuery)
                    } else {
                        val oneRelationshipDefinition = firstPossibleType.oneRelationshipDefinitions[field.name]!!
                        val subQuery = NodeSubQuery(
                            generateOneNodeQuery(oneRelationshipDefinition, field),
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
        manyRelationshipDefinition: ManyRelationshipDefinition, field: SelectedField
    ): NodeQuery {
        val nodeType = manyRelationshipDefinition.nodeKClass
        val nodeDefinition = nodeDefinitionCollection.backingCollection[nodeType]!!
        val filterDefinition = filterDefinitionCollection.backingCollection[nodeType]!!
        val filter = field.arguments["filter"]?.let { filterDefinition.parseFilter(it) }
        val orderBy = field.arguments["orderBy"]?.let { parseOrder(it) } ?: Order(OrderDirection.ASC, IdOrderField)
        val subQueryOptions = QueryOptions(
            filter = filter,
            orderBy = orderBy,
            after = field.arguments["after"]?.let { orderBy.parseCursor(it as String, objectMapper) },
            before = field.arguments["before"]?.let { orderBy.parseCursor(it as String, objectMapper) },
            first = field.arguments["first"] as Int?,
            last = field.arguments["last"] as Int?
        )
        val parts = mapOf(
            "nodes" to field.selectionSet.getFields("nodes"),
            "edges" to field.selectionSet.getFields("edges/node")
        )
        return generateNodeQuery(
            nodeDefinition, parts, subQueryOptions
        )
    }

    private fun generateOneNodeQuery(
        oneRelationshipDefinition: OneRelationshipDefinition,
        field: SelectedField,
    ): NodeQuery {
        val nodeDefinition = nodeDefinitionCollection.backingCollection[oneRelationshipDefinition.nodeKClass]!!
        val subQueryOptions = QueryOptions(first = 1, orderBy = Order(OrderDirection.ASC, IdOrderField))
        return generateNodeQuery(
            nodeDefinition, mapOf("default" to field.selectionSet.fields), subQueryOptions
        )
    }
}

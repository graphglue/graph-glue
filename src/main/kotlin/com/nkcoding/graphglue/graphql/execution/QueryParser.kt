package com.nkcoding.graphglue.graphql.execution

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
import graphql.schema.DataFetchingFieldSelectionSet
import graphql.schema.SelectedField

class QueryParser(
    val nodeDefinitionCollection: NodeDefinitionCollection, val filterDefinitionCollection: FilterDefinitionCollection
) {
    fun generateNodeQuery(
        definition: NodeDefinition, dataFetchingEnvironment: DataFetchingEnvironment, queryOptions: QueryOptions
    ): NodeQuery {
        return generateNodeQuery(
            definition, dataFetchingEnvironment.selectionSet, queryOptions
        )
    }

    private fun generateNodeQuery(
        definition: NodeDefinition, selectionSet: DataFetchingFieldSelectionSet, queryOptions: QueryOptions
    ): NodeQuery {
        val oneSubQueries = ArrayList<NodeSubQuery>()
        val manySubQueries = ArrayList<NodeSubQuery>()
        for (field in selectionSet.fields) {
            if (field.fieldDefinitions.first().hasDirective(NODE_RELATIONSHIP_DIRECTIVE)) {
                val onlyOnTypes = nodeDefinitionCollection.getNodeDefinitionsFromGraphQLNames(field.objectTypeNames)
                val firstPossibleType = onlyOnTypes.first()
                val manyRelationshipDefinition = firstPossibleType.manyRelationshipDefinitions[field.name]
                if (manyRelationshipDefinition != null) {
                    val subQuery = NodeSubQuery(
                        generateManyNodeQuery(manyRelationshipDefinition, field),
                        onlyOnTypes,
                        manyRelationshipDefinition
                    )
                    manySubQueries.add(subQuery)
                } else {
                    val oneRelationshipDefinition = firstPossibleType.oneRelationshipDefinitions[field.name]!!
                    val subQuery = NodeSubQuery(
                        generateOneNodeQuery(oneRelationshipDefinition, field),
                        onlyOnTypes,
                        oneRelationshipDefinition
                    )
                    oneSubQueries.add(subQuery)
                }
            }
        }
        return NodeQuery(definition, queryOptions, oneSubQueries, manySubQueries)
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
            after = field.arguments["after"] as String?,
            before = field.arguments["before"] as String?,
            first = field.arguments["first"] as Int?,
            last = field.arguments["last"] as Int?
        )
        return generateNodeQuery(
            nodeDefinition, field.selectionSet, subQueryOptions
        )
    }

    private fun generateOneNodeQuery(
        oneRelationshipDefinition: OneRelationshipDefinition,
        field: SelectedField,
    ): NodeQuery {
        val nodeDefinition = nodeDefinitionCollection.backingCollection[oneRelationshipDefinition.nodeKClass]!!
        val subQueryOptions = QueryOptions(first = 1)
        return generateNodeQuery(
            nodeDefinition, field.selectionSet, subQueryOptions
        )
    }
}

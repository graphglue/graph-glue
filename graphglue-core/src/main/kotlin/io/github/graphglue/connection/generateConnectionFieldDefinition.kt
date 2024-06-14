package io.github.graphglue.connection

import graphql.Scalars
import graphql.language.EnumValue
import graphql.schema.*
import io.github.graphglue.connection.filter.definition.generateFilterDefinition
import io.github.graphglue.connection.model.Connection
import io.github.graphglue.connection.model.Edge
import io.github.graphglue.connection.order.OrderField
import io.github.graphglue.connection.order.OrderPart
import io.github.graphglue.connection.order.generateOrders
import io.github.graphglue.graphql.extensions.getSimpleName
import io.github.graphglue.graphql.schema.SchemaTransformationContext
import io.github.graphglue.model.Node
import kotlin.reflect.KClass

/**
 * Generates the GraphQL connection field
 *
 * @param nodeType the type of the [Node] elements of the connection
 * @param name the name of the field
 * @param description the description of the field, if null no description
 * @param transformer used to access type caches, filter generator, ...
 * @return the generated field
 */
fun generateConnectionFieldDefinition(
    nodeType: KClass<out Node>, name: String, description: String?, transformer: SchemaTransformationContext
): GraphQLFieldDefinition {
    val nodeName = nodeType.getSimpleName()
    val filter = generateFilterDefinition(nodeType, transformer.subFilterGenerator)
    val orders = generateOrders(
        nodeType, transformer.mappingContext.getPersistentEntity(nodeType.java)!!, transformer.additionalOrderBeans
    )
    val builder = GraphQLFieldDefinition.newFieldDefinition().name(name).description(description).argument {
        it.name("filter").description("Filter for specific items in the connection")
            .type(filter.toGraphQLType(transformer.inputTypeCache))
    }.argument {
        it.name("orderBy").description("Order in which the items are sorted")
            .type(generateOrderGraphQLType(nodeName, orders, transformer))
    }.argument {
        it.name("after").description("Get only items after the cursor").type(Scalars.GraphQLString)
    }.argument {
        it.name("before").description("Get only items before the cursor").type(Scalars.GraphQLString)
    }.argument {
        it.name("first").description("Get the first n items. Must not be used if before is specified")
            .type(Scalars.GraphQLInt)
    }.argument {
        it.name("last").description("Get the last n items. Must not be used if after is specified")
            .type(Scalars.GraphQLInt)
    }.type(GraphQLNonNull(generateConnectionGraphQLType(nodeName, transformer)))
    return if (transformer.includeSkipField) {
        builder.argument {
            it.name("skip").description("Skips n items. First or last MUST be specified, is otherwise ignored")
                .type(Scalars.GraphQLInt)
        }.build()
    } else {
        builder.build()
    }
}

/**
 * Generates the connection type for a specific [Node] type
 * As specified by the connection specification, has a `nodes`, `edges`, `totalCount` and `pageInfo` field
 *
 * @param nodeName the name of the [Node]
 * @return the generated connection [GraphQLOutputType]
 */
private fun generateConnectionGraphQLType(
    nodeName: String, transformer: SchemaTransformationContext
): GraphQLOutputType {
    val name = "${nodeName}Connection"
    return transformer.outputTypeCache.computeIfAbsent(name, GraphQLTypeReference(name)) {
        val type = GraphQLObjectType.newObject().name(name).description("The connection type for ${nodeName}.").field {
            it.name("nodes").description("A list of all nodes of the current page.")
                .type(GraphQLNonNull(GraphQLList(GraphQLNonNull(GraphQLTypeReference(nodeName)))))
        }.field {
            it.name("edges").description("A list of all edges of the current page.").type(
                GraphQLNonNull(GraphQLList(GraphQLNonNull(generateEdgeGraphQLType(nodeName, transformer))))
            )
        }.field {
            it.name("totalCount").description("Identifies the total count of items in the connection.")
                .type(GraphQLNonNull(Scalars.GraphQLInt))
        }.field {
            it.name("pageInfo").description("Information to aid in pagination.")
                .type(GraphQLNonNull(GraphQLTypeReference("PageInfo")))
        }.build()

        transformer.registerFunctionDataFetcher(type, "nodes", Connection::class)
        transformer.registerFunctionDataFetcher(type, "edges", Connection::class)
        transformer.registerFunctionDataFetcher(type, "totalCount", Connection::class)
        transformer.registerPropertyDataFetcher(type, "pageInfo", Connection::class)
        type
    }
}

/**
 * Generates the edge type for the `edges` field of the connection.
 * The generated type has a `node` field, which returns the [Node] associated with the edge, and
 * the `cursor` field, which allows getting nodes after / before the edge
 *
 * @param nodeName the name of the [Node]
 * @return the generated type for the edges
 */
private fun generateEdgeGraphQLType(nodeName: String, transformer: SchemaTransformationContext): GraphQLOutputType {
    val name = "${nodeName}Edge"
    return transformer.outputTypeCache.computeIfAbsent(name, GraphQLTypeReference(name)) {
        val type = GraphQLObjectType.newObject().name(name).description("An edge in a connection.").field {
            it.name("node").description("The item at the end of the edge.")
                .type(GraphQLNonNull(GraphQLTypeReference(nodeName)))
        }.field {
            it.name("cursor").description("A cursor used in pagination.").type(GraphQLNonNull(Scalars.GraphQLString))
        }.build()

        transformer.registerFunctionDataFetcher(type, "node", Edge::class)
        transformer.registerFunctionDataFetcher(type, "cursor", Edge::class)
        type
    }
}

/**
 * Generates the order type with a `direction` and an `field` field, where field is of the specific enum for the
 * [Node] type
 *
 * @param nodeName the name of the [Node]
 * @param orders the possible [OrderPart]s for the [Node] type with the enum name as key
 * @return the generated [GraphQLInputType] used to specific the order in which the nodes of a connection are
 *         returned
 */
private fun generateOrderGraphQLType(
    nodeName: String, orders: Map<String, OrderPart<*>>, transformer: SchemaTransformationContext
): GraphQLInputType {
    val name = "${nodeName}Order"
    val inputType = transformer.inputTypeCache.computeIfAbsent(name, GraphQLTypeReference(name)) {
        GraphQLInputObjectType.newInputObject().name(name).description("Defines the order of a $nodeName list").field {
            it.name("field").description("The field to order by, defaults to ID")
                .type(generateOrderPartGraphQLType(nodeName, orders, transformer)).defaultValueLiteral(EnumValue("ID"))
        }.field {
            it.name("direction").description("The direction to order by, defaults to ASC")
                .type(GraphQLTypeReference("OrderDirection")).defaultValueLiteral(EnumValue("ASC"))
        }.build()
    }
    return GraphQLList(inputType)
}

/**
 * Generates the enum which defines all possible order fields
 *
 * @param nodeName the name of the [Node]
 * @param orders the possible [OrderPart]s for the [Node] type with the enum name as key
 */
private fun generateOrderPartGraphQLType(
    nodeName: String, orders: Map<String, OrderPart<*>>, transformer: SchemaTransformationContext
): GraphQLInputType {
    val name = "${nodeName}OrderField"
    return transformer.inputTypeCache.computeIfAbsent(name, GraphQLTypeReference(name)) {
        val builder = GraphQLEnumType.newEnum().name(name).description("Fields a list of $nodeName can be sorted by")
        for ((fieldName, fieldValue) in orders.entries) {
            builder.value(fieldName, fieldValue, "Order by ${fieldValue.name}")
        }
        builder.build()
    }
}

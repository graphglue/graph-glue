package io.github.graphglue.connection

import graphql.Scalars
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLTypeReference
import io.github.graphglue.connection.filter.definition.generateFilterDefinition
import io.github.graphglue.graphql.extensions.getSimpleName
import io.github.graphglue.graphql.schema.SchemaTransformationContext
import io.github.graphglue.model.Node
import kotlin.reflect.KClass

/**
 * Generates the GraphQL search field
 *
 * @param nodeType the type of the [Node] elements of the search
 * @param name the name of the field
 * @param description the description of the field, if null no description
 * @param transformer used to access type caches, filter generator, ...
 * @return the generated field
 */
fun generateSearchFieldDefinition(
    nodeType: KClass<out Node>, name: String, description: String?, transformer: SchemaTransformationContext
): GraphQLFieldDefinition {
    val nodeName = nodeType.getSimpleName()
    val filter = generateFilterDefinition(nodeType, transformer.subFilterGenerator)
    val builder = GraphQLFieldDefinition.newFieldDefinition().name(name).description(description).argument {
        it.name("query").description("Search query nodes must match").type(GraphQLNonNull(Scalars.GraphQLString))
    }.argument {
        it.name("filter").description("Filter for specific items")
            .type(filter.toGraphQLType(transformer.inputTypeCache))
    }.argument {
        it.name("first").description("Get the first n items.")
            .type(GraphQLNonNull(Scalars.GraphQLInt))
    }.type(GraphQLNonNull(GraphQLList(GraphQLNonNull(GraphQLTypeReference(nodeName)))))
    return if (transformer.includeSkipField) {
        builder.argument {
            it.name("skip").description("Skips n items. First or last MUST be specified, is otherwise ignored")
                .type(Scalars.GraphQLInt)
        }.build()
    } else {
        builder.build()
    }
}
package com.nkcoding.graphglue.graphql.connection

import com.nkcoding.graphglue.graphql.connection.filter.definition.SubFilterGenerator
import com.nkcoding.graphglue.graphql.connection.filter.generateFilterDefinition
import com.nkcoding.graphglue.graphql.extensions.getSimpleName
import com.nkcoding.graphglue.graphql.redirect.REDIRECT_PROPERTY_DIRECTIVE
import com.nkcoding.graphglue.model.Node
import graphql.Scalars
import graphql.schema.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

fun generateWrapperGraphQLType(
    connectionType: KType,
    outputTypeCache: MutableMap<String, GraphQLObjectType>,
    inputTypeCache: MutableMap<String, GraphQLInputObjectType>,
    subFilterGenerator: SubFilterGenerator
): GraphQLType {
    val returnNodeName = connectionType.arguments[0].type!!.jvmErasure.getSimpleName()
    val name = "${returnNodeName}ListWrapper"
    return outputTypeCache.computeIfAbsent(name) {
        @Suppress("UNCHECKED_CAST")
        val filter =
            generateFilterDefinition(
                connectionType.arguments[0].type?.jvmErasure as KClass<out Node>,
                subFilterGenerator
            )

        GraphQLObjectType.newObject()
            .name(name)
            .withDirective(REDIRECT_PROPERTY_DIRECTIVE)
            .field { fieldBuilder ->
                fieldBuilder.name("getFromGraphQL")
                    .withDirective(REDIRECT_PROPERTY_DIRECTIVE)
                    .argument {
                        it.name("filter").type(filter.toGraphQLType(inputTypeCache))
                    }
                    .argument {
                        it.name("after").type(Scalars.GraphQLString)
                    }
                    .argument {
                        it.name("before").type(Scalars.GraphQLString)
                    }
                    .argument {
                        it.name("first").type(Scalars.GraphQLInt)
                    }
                    .argument {
                        it.name("last").type(Scalars.GraphQLInt)
                    }
                    .type(GraphQLNonNull(GraphQLList(GraphQLNonNull(GraphQLTypeReference(returnNodeName)))))
            }
            .build()
    }
}
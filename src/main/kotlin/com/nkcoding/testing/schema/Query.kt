package com.nkcoding.testing.schema

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.operations.Query
import com.nkcoding.testing.model.*
import com.nkcoding.testing.schema.input.TestInput
import graphql.schema.DataFetchingEnvironment
import org.springframework.stereotype.Component
import kotlin.reflect.full.memberProperties

@Component
class Query : Query {

    fun node(id: String): Node {
        return VerySpecialLeaf()
    }

    fun root(): Root {
        return Root()
    }

    fun root2(): Root {
        return Root()
    }

    /*
    @GraphQLDescription("Returns the provided text")
    fun echo(text: String): String = text

    val test: List<Int> = listOf(1, 2, 3);

    fun trees(input: TestInput, environment: DataFetchingEnvironment): List<Tree> {
        //environment.field.arguments[1].value;
        val selectedFields = environment.selectionSet.fields
        val field = environment.field
        val argument = environment.getArgument<Any>("input")
        val argument2 = field.arguments[0]
        val arguments = environment.arguments

        for (prop in com.nkcoding.testing.schema.Query::class.memberProperties) {
            val type = prop.returnType
            val argumentType = type.arguments.first().type!!
            println(argumentType)
        }

        return listOf(Tree())
    }
    */
}
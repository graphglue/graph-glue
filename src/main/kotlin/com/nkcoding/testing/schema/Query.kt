package com.nkcoding.testing.schema

import com.expediagroup.graphql.server.operations.Query
import com.nkcoding.graphglue.model.Node
import com.nkcoding.testing.model.Root
import com.nkcoding.testing.model.Tree
import com.nkcoding.testing.model.VerySpecialLeaf
import org.springframework.stereotype.Component

@Component
class Query : Query {

    fun node(id: String): Node {
        return VerySpecialLeaf("lol")
    }

    fun tree(): Tree {
        return Tree()
    }

    fun root(): Root {
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
package com.nkcoding.testing.schema

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.server.operations.Query
import com.nkcoding.graphglue.graphql.execution.QueryOptions
import com.nkcoding.graphglue.graphql.execution.QueryParser
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinitionCollection
import com.nkcoding.graphglue.model.Node
import com.nkcoding.testing.model.Root
import com.nkcoding.testing.model.Tree
import com.nkcoding.testing.model.VerySpecialLeaf
import graphql.schema.DataFetchingEnvironment
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class Query : Query {

    fun node(
        id: String,
        dfe: DataFetchingEnvironment,
        @Autowired @GraphQLIgnore queryParser: QueryParser,
    ): Node {
        val nodeDefinitionCollection = queryParser.nodeDefinitionCollection
        val parsedQuery =
            queryParser.generateNodeQuery(nodeDefinitionCollection.getNodeDefinition<Node>(), dfe, QueryOptions())
        println()
        return VerySpecialLeaf("lol")
    }

    fun tree(): Tree {
        return Tree()
    }

    fun root(): Root {
        return Root()
    }


    fun echo(text: String): String = text

    val test: List<Int> = listOf(1, 2, 3);

    fun trees(environment: DataFetchingEnvironment): List<Tree> {
        //environment.field.arguments[1].value;
        val selectedFields = environment.selectionSet.fields
        val field = environment.field
        val argument = environment.getArgument<Any>("input")
        val argument2 = field.arguments[0]
        val arguments = environment.arguments

        return listOf(Tree())
    }

}
package com.nkcoding.testing.schema

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.server.operations.Query
import com.fasterxml.jackson.databind.ObjectMapper
import com.nkcoding.graphglue.graphql.execution.QueryOptions
import com.nkcoding.graphglue.graphql.execution.QueryParser
import com.nkcoding.graphglue.graphql.execution.definition.NodeDefinitionCollection
import com.nkcoding.graphglue.model.Node
import com.nkcoding.testing.model.*
import graphql.schema.DataFetchingEnvironment
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.*

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
        return ARoot()
    }

    fun tree(): Tree {
        return Tree()
    }

    fun root(): Root {
        return BRoot()
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

    fun encoderTest(@Autowired @GraphQLIgnore objectMapper: ObjectMapper): String {
        val source = mapOf("x" to 10, "y" to "hello world", "z" to 11.5)
        val encoded = objectMapper.writeValueAsBytes(source)
        val res = Base64.getEncoder().encodeToString(encoded)
        val deBase64 = Base64.getDecoder().decode(res)
        val decoded = objectMapper.readValue(deBase64, Map::class.java)
        println(decoded)
        return res
    }

}
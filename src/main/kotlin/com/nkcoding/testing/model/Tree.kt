package com.nkcoding.testing.model

import com.expediagroup.graphql.generator.annotations.GraphQLType
import com.nkcoding.graphglue.graphql.redirect.RedirectPropertyClass
import com.nkcoding.graphglue.graphql.redirect.RedirectPropertyFunction
import com.nkcoding.testing.schema.input.TestInput


class Tree {

    val id = "Hello world"

    val redirect: RedirectedLeaf = RedirectedLeaf()

    fun leafs(input: TestInput): List<Leaf> {
        return emptyList()
    }

    val alwaysExecuted: String
        get() {
            println("Are you kidding me")
            return "Hello world"
        }
}

@RedirectPropertyClass
class RedirectedLeaf {
    @RedirectPropertyFunction
    fun newType(test: String): Int = 42
}
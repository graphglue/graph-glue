package com.nkcoding.testing.model

import com.nkcoding.graphglue.graphql.redirect.RedirectPropertyClass
import com.nkcoding.graphglue.graphql.redirect.RedirectPropertyFunction
import com.nkcoding.testing.schema.input.TestInput


class Tree {

    val id = "Hello world"

    val redirect: RedirectedLeaf<String> = RedirectedLeaf()

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
class RedirectedLeaf<T> {
    @RedirectPropertyFunction
    fun newType(test: Filter<T>): Int = 42
}

class Filter<T> {
    val x: Int = 101
}
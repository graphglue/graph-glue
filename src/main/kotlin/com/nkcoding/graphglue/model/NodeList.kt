package com.nkcoding.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.nkcoding.graphglue.graphql.connection.filter.model.Filter
import com.nkcoding.graphglue.graphql.connection.order.Order
import com.nkcoding.graphglue.graphql.execution.QueryOptions
import com.nkcoding.graphglue.graphql.execution.QueryParser
import com.nkcoding.graphglue.graphql.redirect.RedirectPropertyClass
import com.nkcoding.testing.model.Leaf
import com.nkcoding.testing.model.VerySpecialLeaf
import graphql.schema.DataFetchingEnvironment
import org.springframework.beans.factory.annotation.Autowired

@RedirectPropertyClass
class NodeList<T : Node> : List<T> {

    fun getFromGraphQL(
        @GraphQLIgnore @Autowired
        queryParser: QueryParser,
        dfe: DataFetchingEnvironment
    ): Connection<T> {
        println(dfe.executionStepInfo.path)
        println(dfe.parentType)
        //queryParser.generateNodeQuery(queryParser.nodeDefinitionCollection.getNodeDefinition<Leaf>(), dfe, QueryOptions())
        return Connection(listOf(VerySpecialLeaf("test"), VerySpecialLeaf("test2")) as List<T>, PageInfo("lol", "lolol", true, true), 10)
        TODO("Implement, add correct types, ...")
    }

    //region list implementation

    override val size: Int
        get() = TODO("Not yet implemented")

    override fun contains(element: T): Boolean {
        TODO("Not yet implemented")
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        TODO("Not yet implemented")
    }

    override fun get(index: Int): T {
        TODO("Not yet implemented")
    }

    override fun indexOf(element: T): Int {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    override fun iterator(): Iterator<T> {
        TODO("Not yet implemented")
    }

    override fun lastIndexOf(element: T): Int {
        TODO("Not yet implemented")
    }

    override fun listIterator(): ListIterator<T> {
        TODO("Not yet implemented")
    }

    override fun listIterator(index: Int): ListIterator<T> {
        TODO("Not yet implemented")
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<T> {
        TODO("Not yet implemented")
    }

    //endregion
}
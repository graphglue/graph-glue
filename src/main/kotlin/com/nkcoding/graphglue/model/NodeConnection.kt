package com.nkcoding.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.nkcoding.graphglue.graphql.filter.Filter
import com.nkcoding.graphglue.graphql.filter.Order
import com.nkcoding.graphglue.graphql.redirect.RedirectPropertyClass

@RedirectPropertyClass
class NodeConnection<T : Node> : List<T> {

    fun getFromGraphQL(
        @GraphQLDescription("Filter for specific items in the connection")
        filter: Filter<T>?,
        @GraphQLDescription("Order in which the items are sorted")
        //order: Order<T>?,
        //@GraphQLDescription("Get only items after the cursor")
        after: String?,
        @GraphQLDescription("Get only items before the cursor")
        before: String?,
        @GraphQLDescription("Get the first n items. Must not be used if before is specified")
        first: Int?,
        @GraphQLDescription("Get the last n items. Must not be used if after is specified")
        last: Int?
    ): List<T> {
        TODO("Implement, add correct types, ...")
        TODO("Order")
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
package com.nkcoding.graphglue.model

import com.nkcoding.graphglue.graphql.connection.filter.model.Filter
import com.nkcoding.graphglue.graphql.connection.order.Order
import com.nkcoding.graphglue.graphql.redirect.RedirectPropertyClass

@RedirectPropertyClass
class NodeList<T : Node> : List<T> {

    fun getFromGraphQL(
        filter: Filter? = null,
        orderBy: Order<T>? = null,
        after: String? = null,
        before: String? = null,
        first: Int? = null,
        last: Int? = null
    ): List<T> {
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
package io.github.graphglue.graphql.datafetcher

import graphql.schema.DataFetchingEnvironment
import io.github.graphglue.model.BaseProperty

class DelegateDataFetchingEnvironment(
    private val parent: DataFetchingEnvironment, private val source: BaseProperty<*>
) : DataFetchingEnvironment by parent {

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getSource() = source as T
}
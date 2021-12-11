package com.nkcoding.graphglue.graphql.filter

import com.expediagroup.graphql.generator.annotations.GraphQLType
import kotlin.reflect.KClass

class FilterDefinition<T: Any>(val entryType: KClass<T>) {
    fun toFilter(): Filter<T> {
        TODO()
    }

    fun toGraphQLType(): GraphQLType {
        TODO()
    }
}
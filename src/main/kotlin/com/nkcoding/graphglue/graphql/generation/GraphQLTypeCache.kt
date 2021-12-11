package com.nkcoding.graphglue.graphql.generation

import graphql.schema.GraphQLType

class GraphQLTypeCache<T : GraphQLType> {
    private val cache = HashMap<String, T>()

    fun buildIfNotInCache(name: String, builder: () -> T): T {
        val cachedType = cache[name]
        return if (cachedType != null) {
            cachedType
        } else {
            val newType = builder()
            cache[name] = newType
            newType
        }
    }
}
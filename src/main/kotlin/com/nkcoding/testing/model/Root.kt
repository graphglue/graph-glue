package com.nkcoding.testing.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import graphql.schema.GraphQLSchema
import org.springframework.beans.factory.annotation.Autowired

@GraphQLDescription("What a nice type")
class Root {
    @GraphQLDescription("What a nice id")
    val id = 100

    fun getInjected(@Autowired @GraphQLIgnore schema: GraphQLSchema): Int {
        println(schema)
        return 42
    }
}
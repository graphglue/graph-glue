package com.nkcoding.testing.schema.input

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("Test input")
data class TestInput(val text: String, val number: Int)

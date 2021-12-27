package com.nkcoding.testing.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.nkcoding.graphglue.graphql.connection.filter.FilterProperty
import com.nkcoding.graphglue.graphql.connection.order.OrderProperty
import com.nkcoding.graphglue.model.Neo4jNode
import com.nkcoding.graphglue.model.Node

@Neo4jNode
@GraphQLDescription("What a nice type")
class Root(
    @FilterProperty
    @OrderProperty
    val subRootCount: Int = 0
) : Node()
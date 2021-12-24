package com.nkcoding.testing.model

import com.nkcoding.graphglue.graphql.connection.filter.FilterProperty
import com.nkcoding.graphglue.graphql.connection.order.OrderProperty
import com.nkcoding.graphglue.model.Node
import com.nkcoding.graphglue.model.NodeProperty
import com.nkcoding.graphglue.model.NodeRelationship
import org.springframework.data.neo4j.core.schema.Relationship

abstract class Leaf(id: String) : Node(id) {
    @FilterProperty
    @OrderProperty
    val text = "I am a leaf"

    @NodeRelationship("ANT", Relationship.Direction.OUTGOING)
    val ant by NodeProperty<Ant>()
}


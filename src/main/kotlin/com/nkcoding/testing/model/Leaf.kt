package com.nkcoding.testing.model

import com.nkcoding.graphglue.graphql.connection.filter.FilterProperty
import com.nkcoding.graphglue.graphql.connection.order.OrderProperty
import com.nkcoding.graphglue.model.Neo4jNode
import com.nkcoding.graphglue.model.Node
import com.nkcoding.graphglue.model.NodeRelationship
import com.nkcoding.graphglue.model.NodeSetProperty
import org.springframework.data.annotation.Transient
import org.springframework.data.neo4j.core.schema.Relationship

@Neo4jNode
class Leaf(
    @FilterProperty
    @OrderProperty
    val text: String = "I am a leaf",
) : Node() {

    @NodeRelationship("ants", Relationship.Direction.OUTGOING)
    @delegate:Transient
    @FilterProperty
    val ants by NodeSetProperty<Ant>()

}


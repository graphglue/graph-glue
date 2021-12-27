package com.nkcoding.testing.model

import com.nkcoding.graphglue.model.*
import org.springframework.data.annotation.Transient
import org.springframework.data.neo4j.core.schema.Relationship


@Neo4jNode
class Tree : Node() {
    @NodeRelationship("root", Relationship.Direction.OUTGOING)
    @delegate:Transient
    val root by NodeProperty<Root>()

    @NodeRelationship("leafs", Relationship.Direction.OUTGOING)
    @delegate:Transient
    val leafs by NodeSetProperty<Leaf>()
}
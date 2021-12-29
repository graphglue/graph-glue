package com.nkcoding.testing.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.nkcoding.graphglue.graphql.connection.filter.FilterProperty
import com.nkcoding.graphglue.graphql.connection.order.OrderProperty
import com.nkcoding.graphglue.model.*
import org.springframework.data.annotation.Transient
import org.springframework.data.neo4j.core.schema.Relationship

/*
@DomainNode
class Tree : Node() {
    @NodeRelationship("root", Relationship.Direction.OUTGOING)
    @delegate:Transient
    val root by NodeProperty<Root>()

    @NodeRelationship("leafs", Relationship.Direction.OUTGOING)
    @delegate:Transient
    val leafs by NodeSetProperty<Leaf>()
}

@DomainNode
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

@DomainNode
class Ant(@FilterProperty val name: String = "anty") : Node()

@DomainNode
@GraphQLDescription("What a nice type")
class Root(
    @FilterProperty
    @OrderProperty
    val subRootCount: Int = 0
) : Node()
 */
package com.nkcoding.testing.model

import com.nkcoding.graphglue.graphql.connection.filter.FilterProperty
import com.nkcoding.graphglue.graphql.connection.order.OrderProperty
import com.nkcoding.graphglue.model.DomainNode
import com.nkcoding.graphglue.model.Node
import com.nkcoding.graphglue.model.NodeRelationship
import org.springframework.data.neo4j.core.schema.Relationship
import org.springframework.data.annotation.Transient

@DomainNode("movies")
class Movie(@FilterProperty @OrderProperty val title: String) : Node() {
    @NodeRelationship("ACTED_IN", Relationship.Direction.INCOMING)
    @delegate:Transient
    @FilterProperty
    val actors by NodeSetProperty<Person>()
}

@DomainNode("persons")
class Person(@FilterProperty @OrderProperty val name: String) : Node() {
    @NodeRelationship("ACTED_IN", Relationship.Direction.OUTGOING)
    @delegate:Transient
    val actedIn by NodeSetProperty<Movie>()
}
package com.nkcoding.testing.model

import com.nkcoding.graphglue.graphql.connection.filter.FilterProperty
import com.nkcoding.graphglue.model.DomainNode
import com.nkcoding.graphglue.model.Node

@DomainNode("movies")
class Movie(@FilterProperty val title: String) : Node() {

}
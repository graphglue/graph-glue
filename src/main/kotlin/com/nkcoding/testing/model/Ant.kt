package com.nkcoding.testing.model

import com.nkcoding.graphglue.graphql.connection.filter.FilterProperty
import com.nkcoding.graphglue.model.Neo4jNode
import com.nkcoding.graphglue.model.Node

@Neo4jNode
class Ant(@FilterProperty val name: String = "anty") : Node()
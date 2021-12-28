package com.nkcoding.testing.model

import com.nkcoding.graphglue.model.Neo4jNode
import com.nkcoding.graphglue.model.Node

@Neo4jNode
class Movie(val title: String) : Node() {

}
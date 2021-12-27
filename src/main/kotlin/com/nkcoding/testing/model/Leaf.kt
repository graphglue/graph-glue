package com.nkcoding.testing.model

import com.nkcoding.graphglue.graphql.connection.filter.FilterProperty
import com.nkcoding.graphglue.graphql.connection.order.OrderProperty
import com.nkcoding.graphglue.model.Neo4jNode
import com.nkcoding.graphglue.model.Node
import org.springframework.data.neo4j.core.schema.Id
import org.springframework.data.neo4j.core.schema.Relationship

@Neo4jNode
class Leaf(
    @FilterProperty
    @OrderProperty
    val text: String = "I am a leaf",
    @Relationship(type = "related", direction = Relationship.Direction.OUTGOING)
    val relatedLeaf: List<RelatedLeaf>
) : Node()

@Neo4jNode
class RelatedLeaf(
    @Id val id: String,
    val lol: String
)


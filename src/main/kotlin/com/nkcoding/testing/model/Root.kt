package com.nkcoding.testing.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.nkcoding.graphglue.model.Node
import com.nkcoding.graphglue.model.NodeList
import com.nkcoding.graphglue.model.NodeProperty
import com.nkcoding.graphglue.model.NodeRelationship
import graphql.schema.GraphQLSchema
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.neo4j.core.schema.Relationship

@GraphQLDescription("What a nice type")
class Root : Node("lol") {

    fun getInjected(@Autowired @GraphQLIgnore schema: GraphQLSchema): Int {
        println(schema)
        return 42
    }

    @GraphQLDescription("All the leafs")
    @NodeRelationship("leafs", Relationship.Direction.OUTGOING)
    val leafs = NodeList<Leaf>()

    @GraphQLDescription("delegated property")
    @NodeRelationship("subLeaf", Relationship.Direction.OUTGOING)
    val subLeaf by NodeProperty<Leaf>()
}
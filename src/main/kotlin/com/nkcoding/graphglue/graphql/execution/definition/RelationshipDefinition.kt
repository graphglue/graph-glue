package com.nkcoding.graphglue.graphql.execution.definition

import com.nkcoding.graphglue.graphql.extensions.getPropertyName
import com.nkcoding.graphglue.model.Node
import org.springframework.data.neo4j.core.schema.Relationship
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

abstract class RelationshipDefinition(
    val property: KProperty1<*, *>,
    val nodeKClass: KClass<out Node>,
    val type: String,
    val direction: Relationship.Direction,
    private val parentKClass: KClass<*>
) {
    val graphQLName get() = property.getPropertyName(parentKClass)
}
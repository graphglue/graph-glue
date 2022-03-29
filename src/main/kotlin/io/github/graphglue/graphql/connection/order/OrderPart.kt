package io.github.graphglue.graphql.connection.order

import de.graphglue.model.Node
import kotlin.reflect.KProperty

abstract class OrderPart<in T : Node>(val property: KProperty<*>, val neo4jPropertyName: String) {
    abstract fun getValue(node: T): Any?
}
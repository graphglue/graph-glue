package com.nkcoding.graphglue.model

import com.nkcoding.graphglue.graphql.redirect.RedirectPropertyDelegateClass
import com.nkcoding.graphglue.graphql.redirect.RedirectPropertyFunction
import com.nkcoding.graphglue.neo4j.execution.NodeQueryResult
import kotlin.reflect.KProperty

@RedirectPropertyDelegateClass
class NodeProperty<T : Node?>(value: T? = null) {
    private var isLoaded = false
    private var currentNode: T? = null
    private var persistedNode: T? = null

    init {
        if (value != null) {
            isLoaded = true
            currentNode = value
        }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Node, property: KProperty<*>): T {
        val value = getCurrentNode()
        return if (property.returnType.isMarkedNullable) {
            value as T
        } else {
            if (value == null) {
                throw IllegalStateException("The non-nullable property $property has a null value")
            }
            value
        }
    }

    operator fun setValue(thisRef: Node, property: KProperty<*>, value: T) {
        val current = getCurrentNode()
        if (value != current) {
            currentNode = current
        }
    }

    @RedirectPropertyFunction
    fun getFromGraphQL(): T {
        TODO()
    }

    private fun getCurrentNode(): T? {
        if (isLoaded) {
            return currentNode
        } else {
            TODO()
        }
    }

    internal fun registerQueryResult(nodeQueryResult: NodeQueryResult<T>) {
        if (!isLoaded) {
            if (nodeQueryResult.nodes.size > 1) {
                throw IllegalArgumentException("Too many  nodes for one side of relation")
            }
            currentNode = nodeQueryResult.nodes.firstOrNull()
            persistedNode = currentNode
            isLoaded = true
        }
    }
}
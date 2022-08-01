package io.github.graphglue.data.repositories

import io.github.graphglue.model.Node


/**
 * Collection of added and removed relationships
 * Note: a [Node] might match none or multiple nodes in the database!
 *
 * @param nodesToAdd nodes to add relationships
 * @param nodesToRemove nodes to remove relationships
 */
data class RelationshipDiff(val nodesToAdd: Collection<Node>, val nodesToRemove: Collection<Node>)

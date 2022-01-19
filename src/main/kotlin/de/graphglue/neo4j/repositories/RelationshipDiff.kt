package de.graphglue.neo4j.repositories

import org.neo4j.cypherdsl.core.Node

/**
 * Collection of added and removed relationships
 * Note: a [Node] might match none or multiple nodes in the database!
 *
 * @property nodesToAdd nodes to add relationships
 * @property nodesToRemove nodes to remove relationships
 */
data class RelationshipDiff(val nodesToAdd: Collection<Node>, val nodesToRemove: Collection<Node>)

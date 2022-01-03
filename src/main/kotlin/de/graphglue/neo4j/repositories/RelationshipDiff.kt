package de.graphglue.neo4j.repositories

import org.neo4j.cypherdsl.core.Node

data class RelationshipDiff(val nodesToAdd: Collection<Node>, val nodesToRemove: Collection<Node>)

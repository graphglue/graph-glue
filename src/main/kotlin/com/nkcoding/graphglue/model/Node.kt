package com.nkcoding.graphglue.model

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

/**
 * Base class for all Nodes
 * This is always added to the schema
 * All domain entities which can be retrieved via the api
 * and should be persisted in the database should inherit from this class
 */
@Neo4jNode
@GraphQLDescription("Base class of all nodes")
abstract class Node
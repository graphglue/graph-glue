package com.nkcoding.graphglue.model

import org.springframework.data.neo4j.core.schema.Node

/**
 * Annotation to mark a class with should be persisted in the Neo4j database
 */
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
@Node
annotation class Neo4jNode

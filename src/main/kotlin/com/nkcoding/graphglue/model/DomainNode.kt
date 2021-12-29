package com.nkcoding.graphglue.model

import org.springframework.data.neo4j.core.schema.Node

/**
 * Annotation to mark a class with should be persisted in the Neo4j database
 * @property topLevelQueryName If not empty, a top level query of this node type is available with the given name
 */
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
@Node
annotation class DomainNode(val topLevelQueryName: String = "")

package com.nkcoding.graphglue.neo4j.execution

import com.nkcoding.graphglue.graphql.execution.definition.RelationshipDefinition

data class NodeSubQueryResult(val result: NodeQueryResult, val relationshipDefinition: RelationshipDefinition)
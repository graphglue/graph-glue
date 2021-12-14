package com.nkcoding.graphglue.graphql.connection.filter.model

import com.nkcoding.graphglue.neo4j.CypherConditionGenerator

data class Filter(val metaFilter: MetaFilter) : CypherConditionGenerator {

}
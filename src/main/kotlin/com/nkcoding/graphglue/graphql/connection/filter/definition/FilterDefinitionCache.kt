package com.nkcoding.graphglue.graphql.connection.filter.definition

import com.nkcoding.graphglue.model.Node
import kotlin.reflect.KClass

typealias FilterDefinitionCache = MutableMap<KClass<out Node>, FilterDefinition<out Node>>
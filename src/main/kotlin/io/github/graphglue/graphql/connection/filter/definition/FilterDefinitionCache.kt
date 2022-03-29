package io.github.graphglue.graphql.connection.filter.definition

import io.github.graphglue.model.Node
import io.github.graphglue.util.CacheMap
import kotlin.reflect.KClass

typealias FilterDefinitionCache = CacheMap<KClass<out Node>, FilterDefinition<out Node>>
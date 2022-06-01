package io.github.graphglue.connection.filter.definition

import io.github.graphglue.model.Node
import io.github.graphglue.util.CacheMap
import kotlin.reflect.KClass

/**
 * Cache for [FilterDefinition]s
 */
typealias FilterDefinitionCache = CacheMap<KClass<out Node>, FilterDefinition<out Node>>
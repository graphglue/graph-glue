package de.graphglue.graphql.connection.filter.definition

import de.graphglue.model.Node
import de.graphglue.util.CacheMap
import kotlin.reflect.KClass

typealias FilterDefinitionCache = CacheMap<KClass<out Node>, FilterDefinition<out Node>>
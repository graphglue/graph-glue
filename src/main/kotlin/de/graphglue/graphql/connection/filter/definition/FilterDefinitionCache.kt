package de.graphglue.graphql.connection.filter.definition

import de.graphglue.model.Node
import kotlin.reflect.KClass

typealias FilterDefinitionCache = MutableMap<KClass<out Node>, FilterDefinition<out Node>>
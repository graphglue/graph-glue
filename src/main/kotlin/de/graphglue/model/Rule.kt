package de.graphglue.model

import de.graphglue.neo4j.authorization.AuthorizationRuleGenerator

/**
 * Rule used in [Authorization] annotations
 * Can be used to define which conditions allow or deny access to a [Node]
 *
 * @property beanRef name of the bean which implements the condition, the bean must be a [AuthorizationRuleGenerator]
 * @property options additional options provided to the [AuthorizationRuleGenerator], e.g. property names.
 *                   Allows building general rule generators
 */
annotation class Rule(val beanRef: String, vararg val options: String)
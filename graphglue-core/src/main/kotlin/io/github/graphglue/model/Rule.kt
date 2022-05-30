package io.github.graphglue.model

import io.github.graphglue.authorization.DisallowRuleGenerator


/**
 * Rule used in [Authorization] annotations
 * Can be used to define which conditions allow or deny access to a [Node]
 *
 * @param beanRef name of the bean which implements the condition, the bean must be a [DisallowRuleGenerator]
 * @param options additional options provided to the [DisallowRuleGenerator], e.g. property names.
 *                   Allows building general rule generators
 */
annotation class Rule(val beanRef: String, vararg val options: String)
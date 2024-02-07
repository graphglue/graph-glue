package io.github.graphglue.definition

import io.github.graphglue.GraphglueCoreConfigurationProperties
import io.github.graphglue.authorization.*
import io.github.graphglue.data.execution.CypherConditionGenerator
import io.github.graphglue.graphql.extensions.getSimpleName
import io.github.graphglue.model.Authorization
import io.github.graphglue.model.Node
import io.github.graphglue.model.Rule
import io.github.graphglue.util.iterateGraph
import org.neo4j.cypherdsl.core.*
import org.springframework.beans.factory.BeanFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSuperclassOf

/**
 * Stores a collection of [NodeDefinition]s
 * Also handles generation and storage of authorization conditions
 *
 * @param backingCollection the provided list of [NodeDefinition]s
 * @param beanFactory used to get condition defining beans for authorization
 * @param configurationProperties configuration properties for Graphglue core
 */
class NodeDefinitionCollection(
    backingCollection: Map<KClass<out Node>, NodeDefinition>,
    private val beanFactory: BeanFactory,
    private val configurationProperties: GraphglueCoreConfigurationProperties
) : Collection<NodeDefinition> by backingCollection.values {
    /**
     * Defensive copy of provided `backingCollection`, used to store [NodeDefinition]s
     */
    private val backingCollection = HashMap(backingCollection)

    /**
     * [NodeDefinition]s by GraphQL name lookup
     */
    private val definitionsByGraphQLName = backingCollection.mapKeys { it.key.getSimpleName() }

    /**
     * Lookup from set of all subtype GraphQL names to common parent type [NodeDefinition]
     * Can be used to generate Cypher queries and conditions more efficiently by not having to check for
     * multiple different labels, but only for a common label
     * Keys contain only GraphQL names of [NodeDefinition]s which are object types in the schema (no interfaces)
     */
    private val supertypeNodeDefinitionLookup = generateSupertypeNodeDefinitionLookup()

    /**
     * Set of all known authorization names
     * Used to detect misspelled authorization names
     */
    private val allAuthorizationNames = backingCollection.values.flatMap { it.authorizations.keys }.toSet()

    /**
     * Lookup for disallow rules by authorization name
     */
    private val authorizationDisallowRules = generateAuthorizationDisallowRules()

    /**
     * Associates each [NodeDefinition] with all sub-NodeDefinitions and super-NodeDefinitions
     */
    private val nodeDefinitionHierarchyLookup = generateNodeDefinitionHierarchyLookup()

    /**
     * For each authorization name and [NodeDefinition], the list of allow rules to check
     */
    private val authorizationAllowRules = generateAuthorizationAllowRules()

    /**
     * For each authorization name, all [NodeDefinition]s that are always allowed
     */
    private val authorizationAllAllowedNodeDefinitions = generateAuthorizationAllAllowedNodeDefinitions()

    /**
     * Generates the supertype node definition lookup
     *
     * @return the generated supertype node definition lookup
     */
    private fun generateSupertypeNodeDefinitionLookup(): Map<Set<String>, NodeDefinition> {
        val supertypeNodeDefinitionLookup = mutableMapOf<Set<String>, NodeDefinition>()
        for ((nodeClass, nodeDefinition) in backingCollection) {
            val subTypes = backingCollection.keys.filter { it.isSubclassOf(nodeClass) }.filter { !it.isAbstract }
                .map { it.getSimpleName() }.toSet()
            supertypeNodeDefinitionLookup[subTypes] = nodeDefinition
        }
        return supertypeNodeDefinitionLookup
    }

    /**
     * Associates each authorization name with a set of all disallow rules (by [NodeDefinition]
     *
     * @return the generated authorization name and [NodeDefinition] to disallow [Rule]s lookup
     */
    private fun generateAuthorizationDisallowRules(): Map<String, Map<NodeDefinition, Set<Rule>>> {
        return allAuthorizationNames.associateWith { name ->
            associateWith {
                it.authorizations[name]?.disallow?.toSet() ?: emptySet()
            }.filterValues { it.isNotEmpty() }
        }
    }

    /**
     * Associates each [NodeDefinition] with all sub-NodeDefinitions and super-NodeDefinitions
     *
     * @return the generated [NodeDefinition] to all [NodeDefinition]s in hierarchy lookup
     */
    private fun generateNodeDefinitionHierarchyLookup(): Map<NodeDefinition, Set<NodeDefinition>> {
        return associateWith { nodeDefinition ->
            filter {
                it.nodeType.isSubclassOf(nodeDefinition.nodeType) || it.nodeType.isSuperclassOf(nodeDefinition.nodeType)
            }.toSet()
        }
    }

    /**
     * Associates each authorization name and [NodeDefinition] with a set of [NodeAllowRule]s used to generate
     * the allow condition
     *
     * @return the lookup from authorization name  and [NodeDefinition] to the generated set of [NodeAllowRule]s
     */
    private fun generateAuthorizationAllowRules(): Map<String, Map<NodeDefinition, Set<NodeAllowRule>>> {
        return allAuthorizationNames.associateWith { name ->
            associateWith { nodeDefinition ->
                generateAuthorizationNodeAllowRules(nodeDefinition, name)
            }
        }
    }

    /**
     * Generates the set of [NodeAllowRule] for a specific authorization name and [NodeDefinition]
     * Also see [generateAuthorizationAllowRules]
     *
     * @param nodeDefinition the [NodeDefinition] for which to generate the allow rules
     * @param name the name of the authorization
     * @return the generated set of [NodeAllowRule]s used to generate the allow condition
     */
    private fun generateAuthorizationNodeAllowRules(
        nodeDefinition: NodeDefinition,
        name: String
    ): Set<NodeAllowRule> {
        val tempAllowRules = mutableSetOf<NodeAllowRule>()
        val allowAllNodeDefinitions = mutableSetOf<NodeDefinition>()
        nodeDefinitionHierarchyLookup[nodeDefinition]!!.iterateGraph { toCheck ->
            val authorization = toCheck.authorizations[name]
            if (authorization?.allowAll == true) {
                allowAllNodeDefinitions += toCheck
            }
            authorization?.allow?.forEach {
                tempAllowRules += NodeAllowRule(
                    setOf(toCheck),
                    it
                )
            }
            getAllowFromRelatedNodeDefinitions(authorization, toCheck)
        }
        return generateFinalAllowRules(tempAllowRules, allowAllNodeDefinitions)
    }

    /**
     * Gets [NodeDefinition] based on [Authorization.allowFromRelated] of a [MergedAuthorization]
     *
     * @param authorization defines the relationships
     * @param nodeDefinition definition for node annotated with a part of [authorization]
     * @return all [NodeDefinition]s from which allow is inherited
     */
    private fun getAllowFromRelatedNodeDefinitions(
        authorization: MergedAuthorization?,
        nodeDefinition: NodeDefinition
    ): List<NodeDefinition> {
        return authorization?.allowFromRelated?.mapNotNull {
            val relationshipDefinition = nodeDefinition.relationshipDefinitionsByProperty[it]
            if (relationshipDefinition == null && !nodeDefinition.nodeType.isAbstract) {
                throw IllegalStateException("Cannot find relationship defined by property $it on $nodeDefinition")
            }
            relationshipDefinition
        }
            ?.flatMap {
                val relatedNodeDefinition = getNodeDefinition(it.nodeKClass)
                nodeDefinitionHierarchyLookup[relatedNodeDefinition]!!
            } ?: emptyList()
    }

    /**
     * Takes a temporary set of [NodeAllowRule] and a set of allow all [NodeDefinition]s and creates the
     * final set of [NodeAllowRule]s
     *
     * @param tempAllowRules already existing [NodeAllowRule]s, merged by [NodeAllowRule.allowRule]
     * @param allowAllNodeDefinitions used to create a new [NodeAllowRule] without a [NodeAllowRule.allowRule]
     * @return the transformed [tempAllowRules] and a new [NodeAllowRule] generated based on [allowAllNodeDefinitions]
     *         (if necessary)
     */
    private fun generateFinalAllowRules(
        tempAllowRules: Set<NodeAllowRule>,
        allowAllNodeDefinitions: Set<NodeDefinition>
    ): Set<NodeAllowRule> {
        val allowRules = tempAllowRules.groupBy { it.allowRule }.values.map { rules ->
            rules.first().copy(nodeDefinitions = rules.flatMap { it.nodeDefinitions }.toSet())
        }.toMutableSet()
        if (allowAllNodeDefinitions.isNotEmpty()) {
            allowRules += NodeAllowRule(
                allowAllNodeDefinitions
            )
        }
        return allowRules
    }

    /**
     * Generates a set of always allowed [NodeDefinition]s for each authorization name
     *
     * @return the lookup from authorization name to always allowed [NodeDefinition]s
     */
    private fun generateAuthorizationAllAllowedNodeDefinitions(): Map<String, Set<NodeDefinition>> {
        return allAuthorizationNames.associateWith { name ->
            filter { it.mergedAuthorizations[name]?.allowAll ?: false }.toSet()
        }
    }

    /**
     * Gets the list of [NodeDefinition]s associated with names
     * If a common supertype is found (and the provided names include all subtypes),
     * returns that supertype
     * Otherwise the provied list is mapped to [NodeDefinition]s
     *
     * @param names the list of GraphQL names
     * @return the found [NodeDefinition]s
     */
    fun getNodeDefinitionsFromGraphQLNames(names: List<String>): List<NodeDefinition> {
        return supertypeNodeDefinitionLookup[names.toSet()]?.let { listOf(it) }
            ?: names.map { definitionsByGraphQLName[it]!! }
    }

    /**
     * Gets a [NodeDefinition] by defining class
     *
     * @param nodeType the defining class
     * @return the found [NodeDefinition]
     * @throws Exception if the provided type does not define a [NodeDefinition]
     */
    fun getNodeDefinition(nodeType: KClass<out Node>): NodeDefinition {
        return backingCollection[nodeType]!!
    }

    /**
     * Gets a [NodeDefinition] by defining class
     *
     * @param T the defining type
     * @return the found [NodeDefinition]
     * @throws Exception if the provided type does not define a [NodeDefinition]
     */
    inline fun <reified T : Node> getNodeDefinition(): NodeDefinition {
        return getNodeDefinition(T::class)
    }

    /**
     * Generates the authorization condition
     *
     * @param nodeDefinition the type to generate the authorization condition for
     * @param permission context for condition creation
     * @return a condition generator which generates the authorization condition
     */
    fun generateAuthorizationCondition(
        nodeDefinition: NodeDefinition, permission: Permission
    ): CypherConditionGenerator {
        return generateAuthorizationCondition(
            nodeDefinition, permission, false
        )
    }

    /**
     * Generates the authorization condition for the remote side of a relationship
     * It is assumed that allow is present on the parent side!
     *
     * @param relationshipDefinition defines the relation to generate the condition for
     * @param permission context for condition creation
     * @return a condition generator which generates the authorization condition when provided the remote side node
     */
    fun generateRelationshipAuthorizationCondition(
        relationshipDefinition: RelationshipDefinition, permission: Permission
    ): CypherConditionGenerator {
        val nodeDefinition = getNodeDefinition(relationshipDefinition.nodeKClass)
        return generateAuthorizationCondition(
            nodeDefinition, permission, checkIfRelationIsAllowed(relationshipDefinition, permission.name)
        )
    }

    /**
     * Generates the authorization condition for a specific type
     *
     * @param nodeDefinition the type to generate the authorization condition for
     * @param permission context for condition creation
     * @param isAllowed if `true`, allow is assumed to be present and only disallow conditions are checked
     * @return a condition generator which generates the authorization condition
     */
    private fun generateAuthorizationCondition(
        nodeDefinition: NodeDefinition, permission: Permission, isAllowed: Boolean
    ): CypherConditionGenerator {
        return if (isAllowed || nodeDefinition in authorizationAllAllowedNodeDefinitions[permission.name]!!) {
            CypherConditionGenerator {
                generateDisallowRule(permission, nodeDefinition, it)
            }
        } else {
            val allowRules = authorizationAllowRules[permission.name]!![nodeDefinition]!!
            if (allowRules.isEmpty()) {
                CypherConditionGenerator { Conditions.isFalse() }
            } else {
                CypherConditionGenerator { node ->
                    generateFullAuthorizationCondition(allowRules, node, permission)
                }
            }
        }
    }

    /**
     * Generates the full authorization condition.
     * This includes both the [allowRules] and all disallow rules.
     *
     * @param allowRules the set of [NodeAllowRule]s to check
     * @param node the [Node] to apply the condition to
     * @param permission context for condition generation
     * @return the generated condition
     */
    private fun generateFullAuthorizationCondition(
        allowRules: Set<NodeAllowRule>,
        node: org.neo4j.cypherdsl.core.Node,
        permission: Permission
    ): Condition {
        val (statement, path, endNode) = if (configurationProperties.useNeo4jPlugin) {
            val pathName = Cypher.name("a__0")
            val nodeName = Cypher.name("a__0")
            val statement = Cypher.call("io.github.graphglue.authorizationPath").withArgs(node.requiredSymbolicName, Cypher.anonParameter(permission.name))
                .yield(Cypher.name("path").`as`(pathName), Cypher.name("node").`as`(nodeName))
            Triple(statement, pathName, Cypher.anyNode(nodeName))
        } else {
            val allowEndNode = Cypher.anyNode("a__0")
            val relationshipStart = node.relationshipTo(allowEndNode).min(0)
                .withProperties(mapOf(permission.name to Cypher.literalTrue()))
            val relationshipName = Cypher.name("a__1")
            val namedRelationship = Cypher.path(relationshipName).definedBy(relationshipStart)
            Triple(Cypher.match(namedRelationship), relationshipName, allowEndNode)
        }

        val nodeInPath = Cypher.name("a__2")
        val nodeDisallowRule = generateDisallowRule(permission, getNodeDefinition<Node>(), Cypher.anyNode(nodeInPath))
        val disallowRule = Predicates.all(nodeInPath).`in`(Functions.nodes(path)).where(nodeDisallowRule)

        val allowExistRules =  allowRules.fold(Conditions.noCondition()) { oldCondition, rule ->
            val nodeDefinitionsCondition =
                rule.nodeDefinitions.fold(Conditions.noCondition()) { oldNodeCondition, definition ->
                    oldNodeCondition.or(endNode.hasLabels(definition.primaryLabel))
                }
            val (relationship, condition) = if (rule.allowRule != null) {
                val ruleGenerator = beanFactory.getBean(rule.allowRule.beanRef, AllowRuleGenerator::class.java)
                ruleGenerator.generateRule(endNode, rule.allowRule, permission)
            } else {
                null to Conditions.noCondition()
            }
            val conditionToApply = nodeDefinitionsCondition.and(condition)
            val wrappedCondition = if (relationship != null) {
                Cypher.match(relationship).where(conditionToApply).asCondition()
            } else {
                conditionToApply
            }
            oldCondition.or(wrappedCondition)
        }
        return statement.where(disallowRule).and(allowExistRules).asCondition()
    }

    /**
     * Checks if a relation is allowed.
     * A relation is allowed if allow on the parent side of the relation implies allow on the remote
     * side of the relation.
     * Can be used to improve authorization checking when fetching nested data structures.
     * Allow is implied if the inverse relation (if existing) is allowed from related or if
     * this is a one-side and allows from the related side (and has no other allow rules, neither other allow from
     * related nor allow rules).
     *
     * @param relationshipDefinition defines the relation to check
     * @param authorizationName name of the authorization, used to obtain the [MergedAuthorization]
     * @return `true` iff allow for the remote nodes is implied
     */
    private fun checkIfRelationIsAllowed(
        relationshipDefinition: RelationshipDefinition, authorizationName: String
    ): Boolean {
        val nodeDefinition = getNodeDefinition(relationshipDefinition.nodeKClass)
        val nodeAuthorization = getMergedAuthorization(authorizationName, nodeDefinition)
        val inverseRelationshipDefinition = nodeDefinition.getRelationshipDefinitionByInverse(relationshipDefinition)
        return if (inverseRelationshipDefinition?.property?.name in nodeAuthorization.allowFromRelated) {
            true
        } else if (relationshipDefinition is OneRelationshipDefinition && relationshipDefinition.parentKClass.isFinal) {
            val parentNodeDefinition = getNodeDefinition(relationshipDefinition.parentKClass)
            val parentAuthorization = getMergedAuthorization(authorizationName, parentNodeDefinition)
            parentAuthorization.allow.isEmpty() && !parentAuthorization.allowAll
                    && parentAuthorization.allowFromRelated.size == 1
                    && relationshipDefinition.property.name in parentAuthorization.allowFromRelated
        } else {
            false
        }
    }

    /**
     * Creates a [Condition] to check for disallowed nodes that must be `true`
     *
     * @param permission the [Permission] to check for
     * @param nodeDefinition definition of the node
     * @param node Cypher DSL node to apply the condition to
     */
    private fun generateDisallowRule(
        permission: Permission,
        nodeDefinition: NodeDefinition,
        node: org.neo4j.cypherdsl.core.Node
    ): Condition {
        val disallowRules = authorizationDisallowRules[permission.name]!!
        val nodeDefinitions = nodeDefinitionHierarchyLookup[nodeDefinition]!! intersect disallowRules.keys
        val rules = nodeDefinitions.map { it to disallowRules[it]!! }
        return if (rules.isEmpty()) {
            Conditions.isTrue()
        } else {
            rules.fold(Conditions.noCondition()) { oldCondition, (definition, nodeDisallowRules) ->
                val disallowCondition = nodeDisallowRules.fold(Conditions.noCondition()) { oldDisallowCondition, rule ->
                    val ruleGenerator = beanFactory.getBean(rule.beanRef, DisallowRuleGenerator::class.java)
                    oldDisallowCondition.and(
                        ruleGenerator.generateRule(
                            node, rule, permission
                        ).not()
                    )
                }
                oldCondition.and(node.hasLabels(definition.primaryLabel).not().or(disallowCondition))
            }
        }
    }

    /**
     * If present, returns `nodeDefinition.mergedAuthorizations[name]`
     * If not, checks if the name is known, it returns an empty [Authorization]
     * Otherwise throws an exception, has this hints at a misspelled name
     *
     * @param name the name of the authorization
     * @param nodeDefinition the [NodeDefinition] to get the authorization from
     * @return the found  [Authorization] or a default empty one
     * @throws IllegalArgumentException if the name is completely unknown
     */
    private fun getMergedAuthorization(name: String, nodeDefinition: NodeDefinition): MergedAuthorization {
        return nodeDefinition.mergedAuthorizations[name] ?: if (name in allAuthorizationNames) {
            MergedAuthorization(name, emptySet(), emptySet(), emptySet(), false)
        } else {
            throw IllegalArgumentException("Potentially wrong permission name: $name")
        }
    }
}

/**
 * Mapping from a [NodeDefinition] to a [AllowRuleGenerator]
 *
 * @param nodeDefinitions the set of associated [NodeDefinition]s
 * @param allowRule the associated [Rule], if not present, no rule is used to generate a [Condition]
 */
private data class NodeAllowRule(
    val nodeDefinitions: Set<NodeDefinition>,
    val allowRule: Rule? = null
)
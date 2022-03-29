package io.github.graphglue.neo4j.execution.definition

import io.github.graphglue.graphql.extensions.getSimpleName
import io.github.graphglue.model.Node
import io.github.graphglue.model.Rule
import io.github.graphglue.neo4j.CypherConditionGenerator
import io.github.graphglue.neo4j.authorization.AuthorizationContext
import io.github.graphglue.neo4j.authorization.AuthorizationRuleGenerator
import io.github.graphglue.neo4j.authorization.MergedAuthorization
import org.neo4j.cypherdsl.core.*
import org.springframework.beans.factory.BeanFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * Stores a collection of [NodeDefinition]s
 * Also handles generation and storage of authorization conditions
 *
 * @param backingCollection the provided list of [NodeDefinition]s
 * @param beanFactory used to get condition defining beans for authorization
 */
class NodeDefinitionCollection(
    backingCollection: Map<KClass<out Node>, NodeDefinition>,
    private val beanFactory: BeanFactory
) {
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
    private val supertypeNodeDefinitionLookup: Map<Set<String>, NodeDefinition>

    /**
     * lookup for all subtype [NodeDefinition]s for a specific [NodeDefinition]
     * Contains only subtypes with declare (new) authorizations
     * Used to generate authorization
     */
    private val authorizationSubtypeNodeDefinitionLookup: Map<NodeDefinition, Set<NodeDefinition>>

    init {
        this.supertypeNodeDefinitionLookup = generateSupertypeNodeDefinitionLookup()
        this.authorizationSubtypeNodeDefinitionLookup = generateAuthorizationSubtypeNodeDefinitionLookup()
    }

    /**
     * Generates the supertype node definition lookup
     *
     * @return the generated supertype node definition lookup
     */
    private fun generateSupertypeNodeDefinitionLookup(): Map<Set<String>, NodeDefinition> {
        val supertypeNodeDefinitionLookup = mutableMapOf<Set<String>, NodeDefinition>()
        for ((nodeClass, nodeDefinition) in backingCollection) {
            val subTypes = backingCollection.keys.filter { it.isSubclassOf(nodeClass) }
                .filter { !it.isAbstract }
                .map { it.getSimpleName() }
                .toSet()
            supertypeNodeDefinitionLookup[subTypes] = nodeDefinition
        }
        return supertypeNodeDefinitionLookup
    }

    /**
     * generates the authorization subtype node definition lookup
     *
     * @return the generated authorization supertype node definition lookup
     */
    private fun generateAuthorizationSubtypeNodeDefinitionLookup(): Map<NodeDefinition, Set<NodeDefinition>> {
        return backingCollection.values.associateWith { nodeDefinition ->
            backingCollection.values.filter {
                it != nodeDefinition
                        && it.nodeType.isSubclassOf(nodeDefinition.nodeType)
                        && it.authorizations != nodeDefinition.authorizations
            }.toSet()
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
     * @param authorizationContext context for condition creation
     * @return a condition generator which generates the authorization condition
     */
    fun generateAuthorizationCondition(
        nodeDefinition: NodeDefinition,
        authorizationContext: AuthorizationContext
    ): CypherConditionGenerator {
        return generateAuthorizationCondition(
            nodeDefinition, authorizationContext, false
        )
    }

    /**
     * Generates the authorization condition for the remote side of a relationship
     * It is assumed that allow is present on the parent side!
     *
     * @param relationshipDefinition defines the relation to generate the condition for
     * @param authorizationContext context for condition creation
     * @return a condition generator which generates the authorization condition when provided the remote side node
     */
    fun generateRelationshipAuthorizationCondition(
        relationshipDefinition: RelationshipDefinition,
        authorizationContext: AuthorizationContext
    ): CypherConditionGenerator {
        val nodeDefinition = getNodeDefinition(relationshipDefinition.nodeKClass)
        return generateAuthorizationCondition(
            nodeDefinition,
            authorizationContext,
            checkIfRelationIsAllowed(relationshipDefinition, authorizationContext.name)
        )
    }

    /**
     * Generates the authorization condition for a specific type
     *
     * @param nodeDefinition the type to generate the authorization condition for
     * @param authorizationContext context for condition creation
     * @param isAllowed if `true`, allow is assumed to be present and only disallow conditions are checked
     * @return a condition generator which generates the authorization condition
     */
    private fun generateAuthorizationCondition(
        nodeDefinition: NodeDefinition,
        authorizationContext: AuthorizationContext,
        isAllowed: Boolean
    ): CypherConditionGenerator {
        return CypherConditionGenerator {
            val authorizationPart = generateAuthorizationConditionInternal(
                it, it, nodeDefinition, authorizationContext, isAllowed
            )
            authorizationPart.toCondition()
        }
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
        relationshipDefinition: RelationshipDefinition,
        authorizationName: String
    ): Boolean {
        val nodeDefinition = getNodeDefinition(relationshipDefinition.nodeKClass)
        val nodeAuthorization = nodeDefinition.authorizations[authorizationName]!!
        val inverseRelationshipDefinition = getInverseRelationshipDefinition(relationshipDefinition)
        return if (inverseRelationshipDefinition in nodeAuthorization.allowFromRelated) {
            true
        } else if (relationshipDefinition is OneRelationshipDefinition) {
            val parentNodeDefinition = getNodeDefinition(relationshipDefinition.parentKClass)
            val parentAuthorization = parentNodeDefinition.authorizations[authorizationName]!!
            parentAuthorization.allow.isEmpty()
                    && parentAuthorization.allowFromRelated.size == 1
                    && relationshipDefinition in parentAuthorization.allowFromRelated
        } else {
            false
        }
    }

    /**
     * Gets the inverse of the relationship if possible
     * @param relationshipDefinition the relationship to get the inverse of
     * @return the reverse relationship or `null` if none was found
     */
    private fun getInverseRelationshipDefinition(relationshipDefinition: RelationshipDefinition): RelationshipDefinition? {
        val nodeDefinition = getNodeDefinition(relationshipDefinition.nodeKClass)
        return nodeDefinition.relationshipDefinitions.values.firstOrNull {
            it.type == relationshipDefinition.type && it.direction != relationshipDefinition.direction
        }
    }

    /**
     * Generates the full authorization condition for an authorization type.
     * Supports non-final authorization types (a final authorization type is a Node subclass where all subclasses
     * do not define additional authorizations).
     *
     * @param node the Cypher-DSL node on which the condition should be applied
     * @param authorizationContext context for condition creation
     * @param pattern the already existing pattern part which might be extended
     * @param nodeDefinition used to obtain the [MergedAuthorization]
     * @param isAllowed if `true`, allow is assumed to be present and only disallow conditions are checked
     * @return the generated  authorization condition part, the optionalPattern is defined iff the pattern is extended
     */
    private fun generateAuthorizationConditionInternal(
        node: org.neo4j.cypherdsl.core.Node,
        pattern: ExposesRelationships<*>,
        nodeDefinition: NodeDefinition,
        authorizationContext: AuthorizationContext,
        isAllowed: Boolean
    ): AuthorizationConditionPart {
        val subNodeDefinitions = authorizationSubtypeNodeDefinitionLookup[nodeDefinition]!!
        return if (subNodeDefinitions.isNotEmpty()) {
            val subNodesCondition = subNodeDefinitions.fold(Conditions.noCondition()) { condition, subNodeDefinition ->
                val typeCondition = node.hasLabels(subNodeDefinition.primaryLabel)
                val subPart = generateFullAuthorizationConditionForFinalType(
                    node, pattern, subNodeDefinition, authorizationContext, isAllowed
                )
                val subCondition = subPart.toCondition()
                condition.or(typeCondition.and(subCondition))
            }
            AuthorizationConditionPart(null, subNodesCondition)
        } else {
            generateFullAuthorizationConditionForFinalType(
                node, pattern, nodeDefinition, authorizationContext, isAllowed
            )
        }
    }

    /**
     * Generates the full authorization condition for a final authorization type
     * (a final authorization type is a Node subclass where all subclasses do not define additional
     * authorizations).
     *
     * @param node the Cypher-DSL node on which the condition should be applied
     * @param authorizationContext context for condition creation
     * @param pattern the already existing pattern part which might be extended
     * @param nodeDefinition used to obtain the [MergedAuthorization]
     * @param isAllowed if `true`, allow is assumed to be present and only disallow conditions are checked
     * @return the generated  authorization condition part, the optionalPattern is defined iff the pattern is extended
     */
    private fun generateFullAuthorizationConditionForFinalType(
        node: org.neo4j.cypherdsl.core.Node,
        pattern: ExposesRelationships<*>,
        nodeDefinition: NodeDefinition,
        authorizationContext: AuthorizationContext,
        isAllowed: Boolean
    ): AuthorizationConditionPart {
        val authorization = nodeDefinition.authorizations[authorizationContext.name]!!
        return if (isAllowed) {
            AuthorizationConditionPart(null, generateDisallowCondition(node, authorization, authorizationContext))
        } else {
            generateFullAuthorizationConditionForFinalType(
                node, authorization, authorizationContext, pattern
            )
        }
    }

    /**
     * Generates the full authorization condition for a final authorization type
     * (a final authorization type is a Node subclass where all subclasses do not define additional
     * authorizations).
     *
     * @param authorization the authorization to convert into a condition
     * @param node the Cypher-DSL node on which the condition should be applied
     * @param authorizationContext context for condition creation
     * @param pattern the already existing pattern part which might be extended
     * @return the generated  authorization condition part, the optionalPattern is defined iff the pattern is extended
     */
    private fun generateFullAuthorizationConditionForFinalType(
        node: org.neo4j.cypherdsl.core.Node,
        authorization: MergedAuthorization,
        authorizationContext: AuthorizationContext,
        pattern: ExposesRelationships<*>
    ): AuthorizationConditionPart {
        val allowCondition = generateAllowCondition(node, authorization, authorizationContext)
        val disallowCondition = generateDisallowCondition(node, authorization, authorizationContext)
        return if (authorization.allowFromRelated.isEmpty()) {
            AuthorizationConditionPart(null, allowCondition.and(disallowCondition))
        } else if (authorization.allowFromRelated.size == 1 && authorization.allow.isEmpty()) {
            generateFullAuthorizationConditionForFinalTypeSingleRelatedAllow(
                authorization, pattern, authorizationContext, disallowCondition
            )
        } else {
            generateFullAuthorizationConditionForFinalTypeRelatedAllows(
                authorization, node, authorizationContext, disallowCondition, allowCondition
            )
        }
    }

    /**
     * Generates the full authorization condition for a final authorization type
     * (a final authorization type is a Node subclass where all subclasses do not define additional
     * authorizations). There must be related allows and may be additional allow conditions.
     *
     * @param authorization the authorization to convert into a condition
     * @param node the Cypher-DSL node on which the condition should be applied
     * @param authorizationContext context for condition creation
     * @param disallowCondition the already generated disallow condition
     * @param allowCondition the already generated allow condition (does not include related allows)
     * @return the generated  authorization condition part, the optionalPattern is never defined
     */
    private fun generateFullAuthorizationConditionForFinalTypeRelatedAllows(
        authorization: MergedAuthorization,
        node: org.neo4j.cypherdsl.core.Node,
        authorizationContext: AuthorizationContext,
        disallowCondition: Condition,
        allowCondition: Condition
    ): AuthorizationConditionPart {
        val allowFromRelatedCondition = authorization.allowFromRelated
            .fold(Conditions.noCondition()) { condition, relationshipDefinition ->
                val relatedNodeDefinition = getNodeDefinition(relationshipDefinition.nodeKClass)
                val relatedNode = Cypher.node(relatedNodeDefinition.primaryLabel)
                val newPattern = relationshipDefinition.generateRelationship(node, relatedNode)
                val newPart = generateAuthorizationConditionInternal(
                    relatedNode, newPattern, relatedNodeDefinition, authorizationContext, false
                )
                val relatedCondition = Cypher.match(newPart.optionalPattern ?: newPattern)
                    .where(newPart.condition)
                    .asCondition()
                condition.or(relatedCondition)
            }
        return AuthorizationConditionPart(
            null,
            disallowCondition.and(allowCondition.or(allowFromRelatedCondition))
        )
    }

    /**
     * Generates the full authorization condition for a final authorization type
     * (a final authorization type is a Node subclass where all subclasses do not define additional
     * authorizations). There must be no allow rules and exactly one related allow.
     *
     * @param authorization the authorization to convert into a condition
     * @param pattern the already existing pattern part which might be extended
     * @param authorizationContext context for condition creation
     * @param disallowCondition the already generated disallow condition
     * @return the generated  authorization condition part, the optionalPattern is always defined
     */
    private fun generateFullAuthorizationConditionForFinalTypeSingleRelatedAllow(
        authorization: MergedAuthorization,
        pattern: ExposesRelationships<*>,
        authorizationContext: AuthorizationContext,
        disallowCondition: Condition
    ): AuthorizationConditionPart {
        val relationshipDefinition = authorization.allowFromRelated.first()
        val relatedNodeDefinition = getNodeDefinition(relationshipDefinition.nodeKClass)
        val relatedNode = Cypher.node(relatedNodeDefinition.primaryLabel)
        val newPattern = relationshipDefinition.generateRelationship(pattern, relatedNode)
        val newPart = generateAuthorizationConditionInternal(
            relatedNode, newPattern, relatedNodeDefinition, authorizationContext, false
        )
        return AuthorizationConditionPart(
            newPart.optionalPattern ?: newPattern,
            newPart.condition.and(disallowCondition)
        )
    }

    /**
     * Generates the combined allow condition
     *
     * @param node the Cypher-DSL node on which the condition should be applied
     * @param authorization contains allow rules
     * @param authorizationContext context for condition creation
     * @return the generated condition which enforces allow rules
     */
    private fun generateAllowCondition(
        node: org.neo4j.cypherdsl.core.Node,
        authorization: MergedAuthorization,
        authorizationContext: AuthorizationContext
    ): Condition {
        return authorization.allow.fold(Conditions.noCondition()) { condition, rule ->
            condition.or(generateConditionForRule(rule, node, authorizationContext))
        }
    }

    /**
     * Generates the combined disallow condition
     *
     * @param node the Cypher-DSL node on which the condition should be applied
     * @param authorization contains disallow rules
     * @param authorizationContext context for condition creation
     * @return the generated condition which enforces disallow rules
     */
    private fun generateDisallowCondition(
        node: org.neo4j.cypherdsl.core.Node,
        authorization: MergedAuthorization,
        authorizationContext: AuthorizationContext
    ): Condition {
        return if (authorization.disallow.isNotEmpty()) {
            authorization.disallow.fold(Conditions.noCondition()) { condition, rule ->
                condition.or(generateConditionForRule(rule, node, authorizationContext))
            }.not()
        } else {
            Conditions.noCondition()
        }
    }

    /**
     * Generates a [Condition] for a [Rule]
     *
     * @param rule the rule to convert
     * @param node the Cypher-DSL node on which the condition should be applied
     * @param authorizationContext context for condition creation
     * @return the generated condition which enforces the rule
     */
    private fun generateConditionForRule(
        rule: Rule,
        node: org.neo4j.cypherdsl.core.Node,
        authorizationContext: AuthorizationContext
    ): Condition {
        val bean = beanFactory.getBean(rule.beanRef, AuthorizationRuleGenerator::class.java)
        return bean.generateCondition(node, rule, authorizationContext)
    }
}

/**
 * Part of an authorization condition
 *
 * @param optionalPattern optional associated [PatternElement]
 * @param condition associated [Condition]
 */
private data class AuthorizationConditionPart(val optionalPattern: PatternElement?, val condition: Condition) {

    /**
     * Converts this into a condition
     * If a [optionalPattern] is present, a subquery is generated
     * Otherwise the condition is returned
     *
     * @return the generated condition
     */
    fun toCondition(): Condition {
        return if (optionalPattern != null) {
            Cypher.match(optionalPattern)
                .where(condition)
                .asCondition()
        } else {
            condition
        }
    }
}
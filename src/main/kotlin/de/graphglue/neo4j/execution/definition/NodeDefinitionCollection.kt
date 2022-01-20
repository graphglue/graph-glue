package de.graphglue.neo4j.execution.definition

import de.graphglue.graphql.extensions.getSimpleName
import de.graphglue.model.Node
import de.graphglue.model.Rule
import de.graphglue.neo4j.CypherConditionGenerator
import de.graphglue.neo4j.authorization.AuthorizationContext
import de.graphglue.neo4j.authorization.AuthorizationRuleGenerator
import de.graphglue.neo4j.authorization.MergedAuthorization
import org.neo4j.cypherdsl.core.*
import org.springframework.beans.factory.BeanFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * Stores a collection of [NodeDefinition]s
 * Also handles generation and storage of authorization conditions
 *
 * @param backingCollection the provided list of [NodeDefinition]s
 * @property beanFactory used to get condition defining beans for authorization
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

    fun generateAuthorizationCondition(
        nodeDefinition: NodeDefinition,
        authorizationContext: AuthorizationContext
    ): CypherConditionGenerator {
        return generateAuthorizationCondition(
            nodeDefinition, authorizationContext, false
        )
    }

    private fun generateAuthorizationCondition(
        nodeDefinition: NodeDefinition,
        authorizationContext: AuthorizationContext,
        isAllowed: Boolean
    ): CypherConditionGenerator {
        return CypherConditionGenerator {
            val authorizationPart = generateAuthorizationConditionInternal(
                it, it, nodeDefinition, authorizationContext, isAllowed
            )
            generateOptionalExistentialSubquery(authorizationPart)
        }
    }

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

    private fun getInverseRelationshipDefinition(relationshipDefinition: RelationshipDefinition): RelationshipDefinition {
        val nodeDefinition = getNodeDefinition(relationshipDefinition.nodeKClass)
        return nodeDefinition.relationshipDefinitions.values.first {
            it.type == relationshipDefinition.type && it.direction != relationshipDefinition.direction
        }
    }

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
                val subCondition = generateOptionalExistentialSubquery(subPart)
                condition.or(typeCondition.and(subCondition))
            }
            AuthorizationConditionPart(null, subNodesCondition)
        } else {
            generateFullAuthorizationConditionForFinalType(
                node, pattern, nodeDefinition, authorizationContext, isAllowed
            )
        }
    }

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

    private fun generateAllowCondition(
        node: org.neo4j.cypherdsl.core.Node,
        authorization: MergedAuthorization,
        authorizationContext: AuthorizationContext
    ): Condition {
        return authorization.allow.fold(Conditions.noCondition()) { condition, rule ->
            condition.or(ruleToCondition(rule, node, authorizationContext))
        }
    }

    private fun generateDisallowCondition(
        node: org.neo4j.cypherdsl.core.Node,
        authorization: MergedAuthorization,
        authorizationContext: AuthorizationContext
    ): Condition {
        return if (authorization.disallow.isNotEmpty()) {
            authorization.disallow.fold(Conditions.noCondition()) { condition, rule ->
                condition.or(ruleToCondition(rule, node, authorizationContext))
            }.not()
        } else {
            Conditions.noCondition()
        }
    }

    private fun ruleToCondition(
        rule: Rule,
        node: org.neo4j.cypherdsl.core.Node,
        authorizationContext: AuthorizationContext
    ): Condition {
        val bean = beanFactory.getBean(rule.beanRef, AuthorizationRuleGenerator::class.java)
        return bean.generateCondition(node, rule, authorizationContext)
    }

    private fun generateOptionalExistentialSubquery(part: AuthorizationConditionPart): Condition {
        return if (part.optionalPattern != null) {
            Cypher.match(part.optionalPattern)
                .where(part.condition)
                .asCondition()
        } else {
            part.condition
        }
    }
}

/**
 * Part of an authorization condition
 *
 * @property optionalPattern optional associated [PatternElement]
 * @property condition associated [Condition]
 */
private data class AuthorizationConditionPart(val optionalPattern: PatternElement?, val condition: Condition)
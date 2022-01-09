package de.graphglue.neo4j.execution.definition

import de.graphglue.graphql.extensions.getSimpleName
import de.graphglue.graphql.extensions.springFindRepeatableAnnotations
import de.graphglue.model.Authorization
import de.graphglue.model.Node
import de.graphglue.neo4j.authorization.MergedAuthorization
import org.neo4j.cypherdsl.core.Expression
import org.neo4j.cypherdsl.core.SymbolicName
import org.springframework.data.neo4j.core.mapping.Constants
import org.springframework.data.neo4j.core.mapping.CypherGenerator
import org.springframework.data.neo4j.core.mapping.Neo4jPersistentEntity
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

class NodeDefinition(
    val nodeType: KClass<out Node>,
    oneRelationshipDefinitions: List<OneRelationshipDefinition>,
    manyRelationshipDefinitions: List<ManyRelationshipDefinition>,
    val persistentEntity: Neo4jPersistentEntity<*>
) {
    val relationshipDefinitions =
        (oneRelationshipDefinitions + manyRelationshipDefinitions).associateBy { it.graphQLName }
    private val relationshipDefinitionsByProperty =
        (oneRelationshipDefinitions + manyRelationshipDefinitions).associateBy { it.property }
    val returnExpression: Expression
    val returnNodeName: SymbolicName
    val authorizations: Map<String, MergedAuthorization> = generateAuthorizations()
    val primaryLabel: String get() = persistentEntity.primaryLabel

    init {
        val expressions = CypherGenerator.INSTANCE.createReturnStatementForMatch(persistentEntity)
        if (expressions.size != 1) {
            throw IllegalStateException("Cannot get return expression for $nodeType, probably due to cyclic references: $expressions")
        }
        returnExpression = expressions.first()
        returnNodeName = Constants.NAME_OF_TYPED_ROOT_NODE.apply(persistentEntity)
    }

    private fun generateAuthorizations(): Map<String, MergedAuthorization> {
        val allAuthorizations = nodeType.springFindRepeatableAnnotations<Authorization>()
        return allAuthorizations.groupBy { it.name }
            .mapValues { (name, authorizations) ->
                val authorization = MergedAuthorization(
                    name,
                    authorizations.flatMap { it.allow.toList() }.toSet(),
                    authorizations.flatMap { it.allowFromRelated.toList() }
                        .map { propertyName ->
                            val property = nodeType.memberProperties.first { it.name == propertyName }
                            relationshipDefinitionsByProperty[property]!!
                        }.toSet(),
                    authorizations.flatMap { it.disallow.toList() }.toSet()
                )
                authorization
            }
    }

    fun getNeo4jNameOfProperty(property: KProperty1<*, *>): String {
        return persistentEntity.getGraphProperty(property.name).orElseThrow().propertyName
    }

    fun getRelationshipDefinitionOfProperty(property: KProperty1<*, *>): RelationshipDefinition {
        return relationshipDefinitionsByProperty[property]!!
    }

    override fun toString() = nodeType.getSimpleName()
}
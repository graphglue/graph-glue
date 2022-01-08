package de.graphglue.neo4j.execution.definition

import de.graphglue.graphql.extensions.getSimpleName
import de.graphglue.model.Node
import org.neo4j.cypherdsl.core.Expression
import org.neo4j.cypherdsl.core.SymbolicName
import org.springframework.data.neo4j.core.mapping.Constants
import org.springframework.data.neo4j.core.mapping.CypherGenerator
import org.springframework.data.neo4j.core.mapping.Neo4jPersistentEntity
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class NodeDefinition(
    val nodeType: KClass<out Node>,
    oneRelationshipDefinitions: List<OneRelationshipDefinition>,
    manyRelationshipDefinitions: List<ManyRelationshipDefinition>,
    val persistentEntity: Neo4jPersistentEntity<*>
) {
    val oneRelationshipDefinitions = oneRelationshipDefinitions.associateBy { it.graphQLName }
    val manyRelationshipDefinitions = manyRelationshipDefinitions.associateBy { it.graphQLName }
    val relationshipDefinitions =
        (oneRelationshipDefinitions + manyRelationshipDefinitions).associateBy { it.graphQLName }
    private val relationshipDefinitionsByProperty =
        (oneRelationshipDefinitions + manyRelationshipDefinitions).associateBy { it.property }
    val returnExpression: Expression
    val returnNodeName: SymbolicName

    init {
        val expressions = CypherGenerator.INSTANCE.createReturnStatementForMatch(persistentEntity)
        if (expressions.size != 1) {
            throw IllegalStateException("Cannot get return expression for $nodeType, probably due to cyclic references: $expressions")
        }
        returnExpression = expressions.first()
        returnNodeName = Constants.NAME_OF_TYPED_ROOT_NODE.apply(persistentEntity)
    }

    fun getNeo4jNameOfProperty(property: KProperty1<*, *>): String {
        return persistentEntity.getGraphProperty(property.name).orElseThrow().propertyName
    }

    fun getRelationshipDefinitionOfProperty(property: KProperty1<*, *>): RelationshipDefinition {
        return relationshipDefinitionsByProperty[property]!!
    }

    override fun toString() = nodeType.getSimpleName()
}
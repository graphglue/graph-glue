package com.nkcoding.graphglue.graphql.execution.definition

import com.nkcoding.graphglue.graphql.extensions.getSimpleName
import com.nkcoding.graphglue.model.Node
import org.neo4j.cypherdsl.core.Cypher
import org.neo4j.cypherdsl.core.Expression
import org.neo4j.cypherdsl.core.SymbolicName
import org.neo4j.driver.types.MapAccessor
import org.neo4j.driver.types.TypeSystem
import org.springframework.data.neo4j.core.mapping.Constants
import org.springframework.data.neo4j.core.mapping.CypherGenerator
import org.springframework.data.neo4j.core.mapping.Neo4jPersistentEntity
import java.util.function.BiFunction
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class NodeDefinition(
    val nodeType: KClass<out Node>,
    oneRelationshipDefinitions: List<OneRelationshipDefinition>,
    manyRelationshipDefinitions: List<ManyRelationshipDefinition>,
    val persistentEntity: Neo4jPersistentEntity<*>,
    val mappingFunction: BiFunction<TypeSystem, MapAccessor, out Node>
) {
    val oneRelationshipDefinitions = oneRelationshipDefinitions.associateBy { it.graphQLName }
    val manyRelationshipDefinitions = manyRelationshipDefinitions.associateBy { it.graphQLName }
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
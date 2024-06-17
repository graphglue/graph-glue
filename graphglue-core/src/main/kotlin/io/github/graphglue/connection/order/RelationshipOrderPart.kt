package io.github.graphglue.connection.order

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.definition.OneRelationshipDefinition
import io.github.graphglue.model.Node
import org.neo4j.cypherdsl.core.Cypher
import org.neo4j.cypherdsl.core.Expression
import org.neo4j.cypherdsl.core.SymbolicName

/**
 * [OrderPart] defined by a [OneRelationshipDefinition] and a related [OrderPart]
 *
 * @param relationshipDefinition the relationship to order by
 * @param relatedOrderPart the order part of the related node
 * @param relatedNodeDefinition the definition of the related node
 */
class RelationshipOrderPart<T : Node>(
    private val relationshipDefinition: OneRelationshipDefinition,
    private val relatedOrderPart: OrderPart<*>,
    private val relatedNodeDefinition: NodeDefinition
) : OrderPart<T>(
    "${relationshipDefinition.property.name}_${relatedOrderPart.name}",
    relatedOrderPart.isNullable || relationshipDefinition.isNullable,
    true
) {

    override fun getExpression(node: SymbolicName): Expression {
        val relatedNode = relatedNodeDefinition.node().named(node.value + "_")
        return Cypher.head(
            Cypher.listBasedOn(
                relationshipDefinition.generateRelationship(
                    Cypher.anyNode(node), relatedNode
                )
            ).returning(relatedOrderPart.getExpression(relatedNode.requiredSymbolicName))
        )
    }
}
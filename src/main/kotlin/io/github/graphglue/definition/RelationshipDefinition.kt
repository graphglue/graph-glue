package io.github.graphglue.definition

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import graphql.schema.GraphQLFieldDefinition
import io.github.graphglue.data.execution.NodeQueryResult
import io.github.graphglue.data.repositories.RelationshipDiff
import io.github.graphglue.graphql.schema.SchemaTransformationContext
import io.github.graphglue.graphql.extensions.getPropertyName
import io.github.graphglue.model.Direction
import io.github.graphglue.model.Node
import io.github.graphglue.model.NodeRelationship
import io.github.graphglue.model.property.NodePropertyDelegate
import org.neo4j.cypherdsl.core.ExposesPatternLengthAccessors
import org.neo4j.cypherdsl.core.ExposesRelationships
import org.neo4j.cypherdsl.core.RelationshipPattern
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.*


/**
 * Defines a relationship between two [Node]s
 * There may or may not be an inverse relation on the foreign node
 *
 * @param property the property on the class which defines the relationship
 * @param nodeKClass the class associated with the item nodes
 * @param type the type of the relation (label associated with Neo4j relationship)
 * @param direction direction of the relation (direction associated with Neo4j relationship)
 * @param parentKClass the class associated with the [NodeDefinition] this is used as part of,
 *                        must be a subclass of the property defining class
 */
abstract class RelationshipDefinition(
    val property: KProperty1<*, *>,
    val nodeKClass: KClass<out Node>,
    val type: String,
    val direction: Direction,
    val parentKClass: KClass<out Node>
) {
    /**
     * GraphQL name of the property
     */
    val graphQLName get() = property.getPropertyName(parentKClass)

    /**
     * Description of the property
     */
    val graphQLDescription get() = property.findAnnotation<GraphQLDescription>()?.value

    /**
     * If true, this exposes a field in the GraphQL API
     */
    val isGraphQLVisible get() = property.visibility == KVisibility.PUBLIC && !property.hasAnnotation<GraphQLIgnore>()

    /**
     * optional setter which is used to initialize the opposite property
     * may only be present if the opposite side is a one side
     */
    private val remotePropertySetter: RemotePropertySetter? = generateRemotePropertySetter()

    /**
     * Creates the remote property setter.
     * Checks all properties on the remote node, and returns the first where
     * - the type matches
     * - the direction is opposite
     * - the property is a one property
     *
     * @return the setter if possible, otherwise null
     */
    private fun generateRemotePropertySetter(): RemotePropertySetter? {
        for (remoteProperty in nodeKClass.memberProperties) {
            val annotation = remoteProperty.findAnnotation<NodeRelationship>()
            if (annotation?.type == type && annotation.direction != direction) {
                if (remoteProperty.returnType.isSubtypeOf(Node::class.createType())) {
                    return { remoteNode, value ->
                        val nodeProperty = remoteNode.getProperty<Node?>(remoteProperty) as NodePropertyDelegate<Node?>
                        nodeProperty.setFromRemote(value)
                    }
                }
            }
        }
        return null
    }

    /**
     * Generates a Cypher-DSL RelationshipPattern
     *
     * @param rootNode the start node of the relationship
     * @param propertyNode the related node
     * @param T the type of the generated relationship
     * @return the generated relationship pattern
     */
    fun <T> generateRelationship(
        rootNode: ExposesRelationships<T>, propertyNode: org.neo4j.cypherdsl.core.Node
    ): T where T : RelationshipPattern, T : ExposesPatternLengthAccessors<*> {
        return when (direction) {
            Direction.OUTGOING -> rootNode.relationshipTo(propertyNode, type)
            Direction.INCOMING -> rootNode.relationshipFrom(propertyNode, type)
        }
    }

    /**
     * Called to register the result of a query
     * might initialize the foreign relation
     *
     * @param node the root node of this relationship
     * @param nodeQueryResult the result of the query, provides foreign nodes
     * @param T the type of foreign node
     */
    internal fun <T : Node> registerQueryResult(node: Node, nodeQueryResult: NodeQueryResult<T>) {
        registerLocalQueryResult(node, nodeQueryResult)
        val setter = remotePropertySetter
        if (setter != null) {
            for (remoteNode in nodeQueryResult.nodes) {
                setter(remoteNode, node)
            }
        }
    }

    /**
     * Registers the query result on the owning side
     * Should update the property of the provided instance
     *
     * @param node the node which contains the property to update
     * @param nodeQueryResult the result of the query
     * @param T the type of nodes of the result
     */
    private fun <T : Node> registerLocalQueryResult(node: Node, nodeQueryResult: NodeQueryResult<T>) {
        node.getProperty<T>(property).registerQueryResult(nodeQueryResult)
    }

    /**
     * Gets the diff describing updates of the property
     *
     * @param node the node which contains the property to get the diff from
     * @param nodeIdLookup node to id lookup, can be used to get id of unpersisted nodes
     * @param nodeDefinition definition of the related nodes
     * @return the diff describing added and removed nodes
     */
    internal fun getRelationshipDiff(
        node: Node,
        nodeIdLookup: Map<Node, String>,
        nodeDefinition: NodeDefinition
    ): RelationshipDiff {
        return node.getProperty<Node>(property).getRelationshipDiff(nodeIdLookup, nodeDefinition)
    }

    /**
     * Gets related nodes to save
     *
     * @param node the node which contains the property to get the related nodes to save
     * @return a list of nodes to save
     */
    internal fun getRelatedNodesToSave(node: Node): Collection<Node> {
        return node.getProperty<Node>(property).getRelatedNodesToSave()
    }

    /**
     * Generates a field definition
     * Important: the field has to be annotated with  [NODE_RELATIONSHIP_DIRECTIVE], otherwise data fetching does
     * not work
     *
     * @param transformationContext used to generate GraphQL types, register data fetchers, ...
     */
    internal abstract fun generateFieldDefinition(transformationContext: SchemaTransformationContext): GraphQLFieldDefinition
}

/**
 * Alias for the setter function for remote properties
 */
private typealias RemotePropertySetter = (remoteNode: Node, value: Node) -> Unit
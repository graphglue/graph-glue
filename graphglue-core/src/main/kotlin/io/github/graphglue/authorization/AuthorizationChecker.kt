package io.github.graphglue.authorization

import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.model.Node
import org.neo4j.cypherdsl.core.Cypher
import org.neo4j.cypherdsl.core.Functions
import org.neo4j.cypherdsl.core.renderer.Renderer
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import reactor.core.publisher.Mono

/**
 * Check if a node allows a certain permission
 * @param collection collection in which the node is defined
 * @param client client used to execute queries
 */
class AuthorizationChecker(
    private val collection: NodeDefinitionCollection,
    private val client: ReactiveNeo4jClient
) {
    /**
     * Check if a node is authorized given a permission
     *
     * @param node Node to check for
     * @param permission Permission to check on the node
     * @return if the permission is granted
     */
    fun hasAuthorization(node: Node, permission: Permission): Mono<Boolean> {
        val nodeDefinition = collection.getNodeDefinition(node::class)
        val conditionGenerator = collection.generateAuthorizationCondition(nodeDefinition, permission)
        val cypherNode = nodeDefinition.node().named("a_node")
            .withProperties(mapOf("id" to Cypher.parameter("a_id", node.rawId!!)))
        val condition = conditionGenerator.generateCondition(cypherNode)
        val statement = Cypher.match(cypherNode).where(condition)
            .returning(Functions.count(Cypher.asterisk()).gt(Cypher.literalOf<Int>(0))).build()
        val queryResult = client.query(Renderer.getDefaultRenderer().render(statement)).bindAll(statement.catalog.parameters)
        return queryResult.fetchAs(Boolean::class.java).one()
    }
}
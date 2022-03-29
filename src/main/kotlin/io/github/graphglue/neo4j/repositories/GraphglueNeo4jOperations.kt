package io.github.graphglue.neo4j.repositories

import io.github.graphglue.model.Node
import io.github.graphglue.neo4j.execution.definition.NodeDefinition
import io.github.graphglue.neo4j.execution.definition.NodeDefinitionCollection
import io.github.graphglue.neo4j.execution.definition.RelationshipDefinition
import org.neo4j.cypherdsl.core.Cypher
import org.neo4j.cypherdsl.core.Statement
import org.neo4j.cypherdsl.core.renderer.Renderer
import org.springframework.beans.factory.BeanFactory
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.ReactiveNeo4jOperations
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * [ReactiveNeo4jOperations] which supports save of lazy loaded relations
 *
 * @param delegate provides [ReactiveNeo4jOperations] functionality
 * @param neo4jClient used to execute Cypher queries
 * @param beanFactory used to get [NodeDefinitionCollection]
 */
class GraphglueNeo4jOperations(
    private val delegate: ReactiveNeo4jOperations,
    private val neo4jClient: ReactiveNeo4jClient,
    private val beanFactory: BeanFactory
) : ReactiveNeo4jOperations by delegate {
    /**
     * used to get [NodeDefinition] by type
     */
    private val nodeDefinitionCollection by lazy { beanFactory.getBean(NodeDefinitionCollection::class.java) }

    /**
     * Saves an instance of an entity
     * For relations managed by SDN: saves all the related entities of the entity.
     * For relations managed by GraphGlue: saves only added related entities
     *
     * @param instance the entity to be saved. Must not be `null`.
     * @param T the type of the entity.
     * @return the saved instance.
     */
    override fun <T : Any?> save(instance: T): Mono<T> {
        return if (instance is Node) {
            saveNode(instance)
        } else {
            delegate.save(instance)
        }
    }

    /**
     * Saves several instances of an entity
     * Implemented by calling [save] for each instance
     *
     * @param instances the instances to be saved. Must not be `null`.
     * @param T the type of the entity.
     * @return the saved instances.
     */
    override fun <T : Any?> saveAll(instances: MutableIterable<T>): Flux<T> {
        return Flux.fromIterable(instances).flatMap(this::save)
    }

    /**
     * Save via projections is not supported
     */
    override fun <T : Any?, R : Any?> saveAs(instance: T, resultType: Class<R>): Mono<R> {
        throw UnsupportedOperationException("Projections are not supported")
    }

    /**
     * Save via projections is not supported
     */
    override fun <T : Any?, R : Any?> saveAllAs(instances: MutableIterable<T>, resultType: Class<R>): Flux<R> {
        throw UnsupportedOperationException("Projections are not supported")
    }

    /**
     * Saves a single Node including all lazy loaded relations
     * Important: it has to be ensured that the returned node is newly loaded from the database
     * Sometimes, the internal save method does not reload it. In this case, we have to find it by id manually
     *
     * @param entity the [Node] to save
     * @return the newly from the database loaded entity
     */
    @Suppress("UNCHECKED_CAST")
    private fun <S> saveNode(entity: Node): Mono<S> {
        val nodesToSave = getNodesToSaveRecursive(entity)
        return Flux.fromIterable(nodesToSave).flatMap { nodeToSave ->
            delegate.save(nodeToSave).map { nodeToSave to it }
        }.collectList().flatMap { saveResult ->
            val savedNodeLookup = saveResult.associate { (nodeToSave, savedNode) ->
                nodeToSave to savedNode
            }
            val nodeIdLookup = savedNodeLookup.mapValues { it.value.rawId!! }
            Flux.fromIterable(nodeIdLookup.keys).flatMap { nodeToSave ->
                val nodeDefinition = nodeDefinitionCollection.getNodeDefinition(nodeToSave::class)
                saveAllRelationships(nodeDefinition, nodeToSave, nodeIdLookup)
            }.then(savedNodeLookup[entity].let {
                if (it === entity) {
                    findById(nodeIdLookup[entity]!!, entity::class.java) as Mono<S>
                } else {
                    Mono.just(it as S)
                }
            })
        }
    }

    /**
     * Saves all relationships of `nodeToSave`
     *
     * @param nodeDefinition the [NodeDefinition] associated with `nodeToSave`
     * @param nodeToSave the [Node] of which lazy loaded relations should be saved
     * @param nodeIdLookup assigns an id to each [Node], used so that newly created nodes have an id
     * @return an empty [Mono] to wait for the end of the operation
     */
    private fun saveAllRelationships(
        nodeDefinition: NodeDefinition,
        nodeToSave: Node,
        nodeIdLookup: Map<Node, String>
    ) = Flux.fromIterable(nodeDefinition.relationshipDefinitions.values).flatMap { relationshipDefinition ->
        val diffToSave = relationshipDefinition.getRelationshipDiff(nodeToSave, nodeIdLookup)
        val deleteMono = Flux.fromIterable(diffToSave.nodesToRemove).flatMap {
            deleteRelationship(relationshipDefinition, nodeIdLookup[nodeToSave]!!, it)
        }.then()
        val addMono = Flux.fromIterable(diffToSave.nodesToAdd).flatMap {
            addRelationship(relationshipDefinition, nodeIdLookup[nodeToSave]!!, it)
        }.then()
        deleteMono.then(addMono)
    }.then()

    /**
     * Adds a relationship
     *
     * @param relationshipDefinition defines the relationship between the nodes
     * @param rootNodeId the id of the [Node] from which the relationship starts
     * @param propertyNode the related `Node`, might stand for multiple [Node]s
     * @return an empty [Mono] to wait for the end of the operation
     */
    private fun addRelationship(
        relationshipDefinition: RelationshipDefinition, rootNodeId: String, propertyNode: org.neo4j.cypherdsl.core.Node
    ): Mono<Void> {
        val idParameter = Cypher.anonParameter(rootNodeId)
        val rootNode = Cypher.anyNode().withProperties(mapOf("id" to idParameter)).named(Cypher.name("node1"))
        val namedPropertyNode = propertyNode.named(Cypher.name("node2"))
        val relationship = relationshipDefinition.generateRelationship(rootNode, namedPropertyNode)
        val statement = Cypher.match(rootNode).match(namedPropertyNode).merge(relationship).build()
        return executeStatement(statement)
    }

    /**
     * Removes a relationship
     *
     * @param relationshipDefinition defines the relationship between the nodes
     * @param rootNodeId the id of the [Node] from which the relationship starts
     * @param propertyNode the related `Node`, might stand for multiple [Node]s
     * @return an empty [Mono] to wait for the end of the operation
     */
    private fun deleteRelationship(
        relationshipDefinition: RelationshipDefinition, rootNodeId: String, propertyNode: org.neo4j.cypherdsl.core.Node
    ): Mono<Void> {
        val idParameter = Cypher.anonParameter(rootNodeId)
        val rootNode = Cypher.anyNode().withProperties(mapOf("id" to idParameter))
        val relationship = relationshipDefinition.generateRelationship(rootNode, propertyNode).named(Cypher.name("rel"))
        val statement = Cypher.match(relationship).delete(relationship.requiredSymbolicName).build()
        return executeStatement(statement)
    }

    /**
     * Executes a Neo4J Statement using the client
     *
     * @param statement the [Statement] to execute
     * @return an empty [Mono] to wait for the end of the operation
     */
    private fun executeStatement(statement: Statement): Mono<Void> {
        return neo4jClient.query(Renderer.getDefaultRenderer().render(statement)).bindAll(statement.parameters).run()
            .then()
    }

    /**
     * Gets a set of nodes which should be saved
     *
     * @param node the node to traverse the relationships from
     * @return the set of [Node]s to be saved
     */
    private fun getNodesToSaveRecursive(node: Node): Set<Node> {
        val nodesToSave = HashSet<Node>()
        val nodesToVisit = ArrayDeque(listOf(node))
        while (nodesToVisit.isNotEmpty()) {
            val nextNode = nodesToVisit.removeFirst()
            if (nextNode !in nodesToSave) {
                nodesToSave.add(nextNode)
                val nodesToAdd = getNodesToSave(nextNode).filter { !nodesToSave.contains(it) }
                nodesToVisit.addAll(nodesToAdd)
            }
        }
        return nodesToSave
    }

    /**
     * Gets the related nodes to save of a node
     *
     * @param node the [Node] to get the related nodes to save of
     * @return a collection with all related nodes to save
     */
    private fun getNodesToSave(node: Node): Collection<Node> {
        val nodeDefinition = nodeDefinitionCollection.getNodeDefinition(node::class)
        return nodeDefinition.relationshipDefinitions.values.flatMap {
            it.getRelatedNodesToSave(node)
        }
    }
}
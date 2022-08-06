package io.github.graphglue.data.repositories

import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.definition.RelationshipDefinition
import io.github.graphglue.model.Direction
import io.github.graphglue.model.Node
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
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> save(instance: T): Mono<T> {
        return if (instance is Node) {
            saveNodes(listOf(instance)).next() as Mono<T>
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
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> saveAll(instances: MutableIterable<T>): Flux<T> {
        val (nodes, others) = instances.partition { it is Node }
        return Flux.concat(
            delegate.saveAll(others), saveNodes(nodes as Collection<Node>) as Flux<T>
        )
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
     * @param entities the [Node]s to save
     * @return the saved nodes
     */
    private fun saveNodes(entities: Iterable<Node>): Flux<Node> {
        val nodesToSave = getNodesToSaveRecursive(entities)
        validateNodes(nodesToSave)
        return Flux.fromIterable(nodesToSave).flatMap { nodeToSave ->
            delegate.save(nodeToSave).map { nodeToSave to it }
        }.collectList().flatMapMany { saveResult ->
            val savedNodeLookup = saveResult.associate { (nodeToSave, savedNode) ->
                nodeToSave to savedNode
            }
            val nodeIdLookup = savedNodeLookup.mapValues { it.value.rawId!! }
            Flux.fromIterable(nodeIdLookup.keys).flatMap { nodeToSave ->
                val nodeDefinition = nodeDefinitionCollection.getNodeDefinition(nodeToSave::class)
                saveAllRelationships(nodeDefinition, nodeToSave, nodeIdLookup)
            }.thenMany(Flux.fromIterable(entities).concatMap { entity ->
                savedNodeLookup[entity].let {
                    if (it === entity) {
                        findById(nodeIdLookup[entity]!!, entity::class.java)
                    } else {
                        Mono.just(it!!)
                    }
                }
            })
        }
    }

    /**
     * Validates all relationship properties of all provided [nodes]
     *
     * @param nodes all currently saved nodes
     */
    private fun validateNodes(nodes: Set<Node>) {
        for (node in nodes) {
            val nodeDefinition = nodeDefinitionCollection.getNodeDefinition(node::class)
            for (relationshipDefinition in nodeDefinition.relationshipDefinitions.values) {
                relationshipDefinition.validate(node, nodes, nodeDefinitionCollection)
            }
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
        nodeDefinition: NodeDefinition, nodeToSave: Node, nodeIdLookup: Map<Node, String>
    ) = Flux.fromIterable(nodeDefinition.relationshipDefinitions.values).flatMap { relationshipDefinition ->
        val relatedNodeDefinition = nodeDefinitionCollection.getNodeDefinition(relationshipDefinition.nodeKClass)
        val diffToSave = relationshipDefinition.getRelationshipDiff(nodeToSave, relatedNodeDefinition)
        val deleteMono = Flux.fromIterable(diffToSave.nodesToRemove).flatMap {
            val nodeId = nodeIdLookup[nodeToSave]!!
            val relatedNodeId = it.rawId!!
            val type = relationshipDefinition.type
            if (relationshipDefinition.direction == Direction.OUTGOING) {
                deleteRelationship(type, nodeId, relatedNodeId, nodeDefinition, relatedNodeDefinition)
            } else {
                deleteRelationship(type, relatedNodeId, relatedNodeId, relatedNodeDefinition, nodeDefinition)
            }
        }.then()
        val addMono = Flux.fromIterable(diffToSave.nodesToAdd).flatMap {
            addRelationship(relationshipDefinition, nodeDefinition, nodeToSave, it, nodeIdLookup)
        }.then()
        deleteMono.then(addMono)
    }.then()

    /**
     * Adds a relationship, where the relationship can have any direction.
     *
     * @param relationshipDefinition definition for the relationship to create
     * @param nodeDefinition definition of the start node
     * @param node the start node, relative to [relationshipDefinition]
     * @param relatedNode the end node, relative to [relationshipDefinition]
     * @param nodeIdLookup assigns an id to each [Node], used so that newly created nodes have an id
     */
    private fun addRelationship(
        relationshipDefinition: RelationshipDefinition,
        nodeDefinition: NodeDefinition,
        node: Node,
        relatedNode: Node,
        nodeIdLookup: Map<Node, String>
    ): Mono<Void> {
        val relatedNodeDefinition = nodeDefinitionCollection.getNodeDefinition(relatedNode::class)
        val inverseRelationshipDefinition =
            relatedNodeDefinition.getRelationshipDefinitionByInverse(relationshipDefinition)
        val nodeId = nodeIdLookup[node] ?: throw IllegalStateException()
        val relatedNodeId = nodeIdLookup[relatedNode] ?: throw IllegalStateException()
        return if (relationshipDefinition.direction == Direction.OUTGOING) {
            addRelationship(
                relationshipDefinition.type,
                relationshipDefinition,
                inverseRelationshipDefinition,
                nodeId,
                relatedNodeId,
                nodeDefinition,
                relatedNodeDefinition
            )
        } else {
            addRelationship(
                relationshipDefinition.type,
                inverseRelationshipDefinition,
                relationshipDefinition,
                relatedNodeId,
                nodeId,
                relatedNodeDefinition,
                nodeDefinition
            )
        }
    }

    /**
     * Adds a relationship, where the [relationshipDefinition] has (if present) direction OUTGOING
     * and [inverseRelationshipDefinition] has direction INCOMING
     * If necessary, also adds a reverse relationship with _type for authorization purposes
     *
     * @param type the type for the generated relationship
     * @param relationshipDefinition if existing, the definition in the direction of the relationship
     * @param inverseRelationshipDefinition if existing, the definition in the inverse direction of the relationship
     * @param rootNodeId the id of the start node of the relationship
     * @param relatedNodeId the id of the end node of the relationship
     * @param rootNodeDefinition the definition of the root node
     * @param relatedNodeDefinition the definition of the related node
     * @return an empty [Mono] to wait for the end of the operation
     */
    private fun addRelationship(
        type: String,
        relationshipDefinition: RelationshipDefinition?,
        inverseRelationshipDefinition: RelationshipDefinition?,
        rootNodeId: String,
        relatedNodeId: String,
        rootNodeDefinition: NodeDefinition,
        relatedNodeDefinition: NodeDefinition
    ): Mono<Void> {
        val rootIdParameter = Cypher.anonParameter(rootNodeId)
        val relatedIdParameter = Cypher.anonParameter(relatedNodeId)
        val rootNode =
            rootNodeDefinition.node().named("node1").withProperties(mapOf("id" to rootIdParameter))
        val relatedNode =
            relatedNodeDefinition.node().named("node2").withProperties(mapOf("id" to relatedIdParameter))
        val relationship = rootNode.relationshipTo(relatedNode, type)
            .withProperties(relationshipDefinition?.allowedAuthorizations?.associateWith { Cypher.literalTrue() }
                ?: emptyMap())
        val builder = Cypher.match(rootNode).match(relatedNode).merge(relationship)
        val statement =
            if (inverseRelationshipDefinition != null && inverseRelationshipDefinition.allowedAuthorizations.isNotEmpty()) {
                val inverseRelationship = rootNode.relationshipFrom(relatedNode, "_$type")
                    .withProperties(inverseRelationshipDefinition.allowedAuthorizations.associateWith { Cypher.literalTrue() })
                builder.merge(inverseRelationship)
            } else {
                builder
            }.build()
        return executeStatement(statement)
    }

    /**
     * Removes a relationship
     *
     * @param type the type of the relationship
     * @param rootNodeId the id of the [Node] from which the relationship starts
     * @param relatedNodeId the id of the [Node] where the relationship ends
     * @param rootNodeDefinition the definition of the node with [rootNodeId]
     * @param relatedNodeDefinition the definition of the node with [relatedNodeId]
     * @return an empty [Mono] to wait for the end of the operation
     */
    private fun deleteRelationship(
        type: String,
        rootNodeId: String,
        relatedNodeId: String,
        rootNodeDefinition: NodeDefinition,
        relatedNodeDefinition: NodeDefinition
    ): Mono<Void> {
        val idParameter = Cypher.anonParameter(rootNodeId)
        val relatedIdParameter = Cypher.anonParameter(relatedNodeId)
        val rootNode = rootNodeDefinition.node().named("node1").withProperties(mapOf("id" to idParameter))
        val relatedNode = relatedNodeDefinition.node().named("node2").withProperties(mapOf("id" to relatedIdParameter))
        val relationship = rootNode.relationshipTo(relatedNode, type)
        val inverseRelationship = rootNode.relationshipFrom(relatedNode, "_$type")
        val statement = Cypher.optionalMatch(relationship, inverseRelationship)
            .delete(relationship.requiredSymbolicName, inverseRelationship.requiredSymbolicName).build()
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
     * @param nodes the nodes to traverse the relationships from
     * @return the set of [Node]s to be saved
     */
    private fun getNodesToSaveRecursive(nodes: Iterable<Node>): Set<Node> {
        val nodesToSave = HashSet<Node>()
        val nodesToVisit = ArrayDeque(nodes.toList())
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
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
import org.springframework.dao.OptimisticLockingFailureException
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
 * @param renderer used to render Cypher queries
 */
class GraphglueNeo4jOperations(
    private val delegate: ReactiveNeo4jOperations,
    private val neo4jClient: ReactiveNeo4jClient,
    private val beanFactory: BeanFactory,
    private val renderer: Renderer
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
    override fun <T : Any> save(instance: T): Mono<T> {
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
            delegate.save(nodeToSave).onErrorMap {
                if (it is OptimisticLockingFailureException) {
                    OptimisticLockingFailureException("The node ${nodeToSave::class.simpleName} with id ${nodeToSave.rawId} was modified by another transaction")
                } else {
                    it
                }
            }.map { nodeToSave to it }
        }.collectList().flatMapMany { saveResult ->
            val savedNodeLookup = saveResult.associate { (nodeToSave, savedNode) ->
                nodeToSave to savedNode
            }
            val nodeIdLookup = savedNodeLookup.mapValues { it.value.rawId!! }
            saveAllRelationships(nodeIdLookup).thenMany(Flux.fromIterable(entities).concatMap { entity ->
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
            for (relationshipDefinition in nodeDefinition.relationshipDefinitions) {
                relationshipDefinition.validate(node, nodes, nodeDefinitionCollection)
            }
        }
    }

    /**
     * Saves all relationships of `nodeToSave`
     *
     * @param nodesToSave the [Node]s of which lazy loaded relations should be saved, mapped to their id
     * @return an empty [Mono] to wait for the end of the operation
     */
    private fun saveAllRelationships(nodesToSave: Map<Node, String>): Mono<Void> {
        val groupedAddedRelationships = mutableMapOf<RelationshipGroupKey, MutableSet<Relationship>>()
        val groupedRemovedRelationships = mutableMapOf<RelationshipGroupKey, MutableSet<Relationship>>()
        for (nodeToSave in nodesToSave.keys) {
            val nodeDefinition = nodeDefinitionCollection.getNodeDefinition(nodeToSave::class)
            for (relationshipDefinition in nodeDefinition.relationshipDefinitions) {
                val diffToSave = relationshipDefinition.getRelationshipDiff(nodeToSave)
                groupedAddedRelationships.addAggregationEntry(
                    nodeDefinition, relationshipDefinition, nodeToSave, diffToSave.nodesToAdd, nodesToSave
                )
                groupedRemovedRelationships.addAggregationEntry(
                    nodeDefinition, relationshipDefinition, nodeToSave, diffToSave.nodesToRemove, nodesToSave
                )
            }
        }
        return Flux.fromIterable(groupedRemovedRelationships.entries).flatMap { (key, relationships) ->
            deleteRelationships(key, relationships)
        }.thenMany(Flux.fromIterable(groupedAddedRelationships.entries).flatMap { (key, relationships) ->
            addRelationships(key, relationships)
        }).then()
    }

    /**
     * Groups all [relatedNodes] by their [RelationshipGroupKey] and adds them to the [MutableMap]
     *
     * @param nodeDefinition the [NodeDefinition] of the node
     * @param relationshipDefinition the [RelationshipDefinition] of the relationship of [node] to all nodes in [relatedNodes]
     * @param node the start node of the relationships
     * @param relatedNodes the end nodes of the relationships
     * @param nodeIdLookup a map from [Node] to their id
     */
    private fun MutableMap<RelationshipGroupKey, MutableSet<Relationship>>.addAggregationEntry(
        nodeDefinition: NodeDefinition,
        relationshipDefinition: RelationshipDefinition,
        node: Node,
        relatedNodes: Collection<Node>,
        nodeIdLookup: Map<Node, String>
    ) {
        relatedNodes.forEach {
            val relatedNodeDefinition = nodeDefinitionCollection.getNodeDefinition(it::class)
            val inverseRelationshipDefinition =
                relatedNodeDefinition.getRelationshipDefinitionByInverse(relationshipDefinition)
            if (relationshipDefinition.direction == Direction.OUTGOING) {
                val key = RelationshipGroupKey(
                    relationshipDefinition, inverseRelationshipDefinition, nodeDefinition, relatedNodeDefinition
                )
                computeIfAbsent(key) { mutableSetOf() }.add(Relationship(nodeIdLookup[node]!!, nodeIdLookup[it]!!))
            } else {
                val key = RelationshipGroupKey(
                    inverseRelationshipDefinition, relationshipDefinition, relatedNodeDefinition, nodeDefinition
                )
                computeIfAbsent(key) { mutableSetOf() }.add(Relationship(nodeIdLookup[it]!!, nodeIdLookup[node]!!))
            }
        }
    }

    /**
     * Adds a group of relationships, where the `key.relationshipDefinition` has (if present) direction OUTGOING
     * and `key.inverseRelationshipDefinition` has direction INCOMING
     * If necessary, also adds reverse relationships with _type for authorization purposes
     *
     * @param relationships the relationships to add
     * @param key defines the relationship
     * @return an empty [Mono] to wait for the end of the operation
     */
    private fun addRelationships(
        key: RelationshipGroupKey,
        relationships: Set<Relationship>,
    ): Mono<Void> {
        val (relationshipDefinition, inverseRelationshipDefinition, rootNodeDefinition, relatedNodeDefinition) = key
        val relationshipsParameter =
            Cypher.anonParameter((relationships.map { mapOf("from" to it.from, "to" to it.to) }))
        val relationshipName = Cypher.name("r")
        val rootNode =
            rootNodeDefinition.node().named("node1").withProperties(mapOf("id" to relationshipName.property("from")))
        val relatedNode =
            relatedNodeDefinition.node().named("node2").withProperties(mapOf("id" to relationshipName.property("to")))
        val relationship = rootNode.relationshipTo(relatedNode, key.type)
            .withProperties(relationshipDefinition?.allowedAuthorizations?.associateWith { Cypher.literalTrue() }
                ?: emptyMap())
        val builder = Cypher.unwind(relationshipsParameter).`as`(relationshipName).match(rootNode).match(relatedNode)
            .merge(relationship)
        val statement =
            if (inverseRelationshipDefinition != null && inverseRelationshipDefinition.allowedAuthorizations.isNotEmpty()) {
                val inverseRelationship = rootNode.relationshipFrom(relatedNode, "_${key.type}")
                    .withProperties(inverseRelationshipDefinition.allowedAuthorizations.associateWith { Cypher.literalTrue() })
                builder.merge(inverseRelationship)
            } else {
                builder
            }.build()
        return executeStatement(statement)
    }

    /**
     * Deletes a group of relationships
     * The relationships are defined by the `key.relationshipDefinition` and `key.inverseRelationshipDefinition`
     *
     * @param key the key defining the relationships
     * @param relationships the relationships to delete
     * @return an empty [Mono] to wait for the end of the operation
     */
    private fun deleteRelationships(
        key: RelationshipGroupKey, relationships: Set<Relationship>
    ): Mono<Void> {
        val relationshipsParameter =
            Cypher.anonParameter((relationships.map { mapOf("from" to it.from, "to" to it.to) }))
        val relationshipName = Cypher.name("r")
        val rootNode =
            key.nodeDefinition.node().named("node1").withProperties(mapOf("id" to relationshipName.property("from")))
        val relatedNode = key.relatedNodeDefinition.node().named("node2")
            .withProperties(mapOf("id" to relationshipName.property("to")))
        val relationship = rootNode.relationshipTo(relatedNode, key.type).named("r1")
        val inverseRelationship = rootNode.relationshipFrom(relatedNode, "_${key.type}").named("r2")
        val statement = Cypher.unwind(relationshipsParameter).`as`(relationshipName).match(relationship)
            .optionalMatch(inverseRelationship)
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
        return neo4jClient.query(renderer.render(statement)).bindAll(statement.catalog.parameters).run().then()
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
        return nodeDefinition.relationshipDefinitions.flatMap {
            it.getRelatedNodesToSave(node)
        }
    }
}

/**
 * Represents a key for a relationship group
 *
 * @param relationshipDefinition the relationship definition
 * @param inverseRelationshipDefinition the inverse relationship definition
 * @param nodeDefinition the root node definition
 * @param relatedNodeDefinition the related node definition
 */
private data class RelationshipGroupKey(
    val relationshipDefinition: RelationshipDefinition?,
    val inverseRelationshipDefinition: RelationshipDefinition?,
    val nodeDefinition: NodeDefinition,
    val relatedNodeDefinition: NodeDefinition
) {
    init {
        require(relationshipDefinition != null || inverseRelationshipDefinition != null) {
            "At least one of relationshipDefinition or inverseRelationshipDefinition must be set"
        }
        require(
            (relationshipDefinition == null || inverseRelationshipDefinition == null) || (relationshipDefinition.type == inverseRelationshipDefinition.type)
        ) {
            "The types of relationshipDefinition and inverseRelationshipDefinition must be equal"
        }
    }

    /**
     * The type of the relationship
     */
    val type get() = relationshipDefinition?.type ?: inverseRelationshipDefinition?.type!!
}

/**
 * Represents a relationship from one node to another
 *
 * @param from the id of the start of the relationship
 * @param to the id of the end of the relationship
 */
private data class Relationship(val from: String, val to: String)
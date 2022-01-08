package de.graphglue.neo4j.repositories

import de.graphglue.graphql.execution.definition.NodeDefinition
import de.graphglue.graphql.execution.definition.NodeDefinitionCollection
import de.graphglue.graphql.execution.definition.RelationshipDefinition
import de.graphglue.model.Node
import org.neo4j.cypherdsl.core.Cypher
import org.neo4j.cypherdsl.core.Statement
import org.neo4j.cypherdsl.core.renderer.Renderer
import org.springframework.beans.factory.BeanFactory
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.ReactiveNeo4jOperations
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class GraphglueNeo4jOperations(
    private val delegate: ReactiveNeo4jOperations,
    private val neo4jClient: ReactiveNeo4jClient,
    private val beanFactory: BeanFactory
) : ReactiveNeo4jOperations by delegate {
    private val nodeDefinitionCollection by lazy { beanFactory.getBean(NodeDefinitionCollection::class.java) }

    override fun <T : Any?> save(instance: T): Mono<T> {
        return if (instance is Node) {
            saveNode(instance)
        } else {
            delegate.save(instance)
        }
    }

    override fun <T : Any?> saveAll(instances: MutableIterable<T>): Flux<T> {
        return Flux.fromIterable(instances).flatMap(this::save)
    }

    override fun <T : Any?, R : Any?> saveAs(instance: T, resultType: Class<R>): Mono<R> {
        throw UnsupportedOperationException("Projections are not supported")
    }

    override fun <T : Any?, R : Any?> saveAllAs(instances: MutableIterable<T>, resultType: Class<R>): Flux<R> {
        throw UnsupportedOperationException("Projections are not supported")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <S> saveNode(entity: Node): Mono<S> {
        val nodesToSave = getNodesToSaveRecursive(entity)
        return Flux.fromIterable(nodesToSave).flatMap { nodeToSave ->
            delegate.save(nodeToSave).map { nodeToSave to it }
        }.collectList().flatMap { saveResult ->
            val nodeIdLookup = saveResult.associate { (nodeToSave, savedNode) ->
                nodeToSave to savedNode.rawId!!
            }
            Flux.fromIterable(nodeIdLookup.keys).flatMap { nodeToSave ->
                val nodeDefinition = nodeDefinitionCollection.getNodeDefinition(nodeToSave::class)
                saveAllRelationships(nodeDefinition, nodeToSave, nodeIdLookup)
            }.then(findById(nodeIdLookup[entity]!!, entity::class.java) as Mono<S>)
        }
    }

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

    private fun deleteRelationship(
        relationshipDefinition: RelationshipDefinition, rootNodeId: String, propertyNode: org.neo4j.cypherdsl.core.Node
    ): Mono<Void> {
        val idParameter = Cypher.anonParameter(rootNodeId)
        val rootNode = Cypher.anyNode().withProperties(mapOf("id" to idParameter))
        val relationship = relationshipDefinition.generateRelationship(rootNode, propertyNode).named(Cypher.name("rel"))
        val statement = Cypher.match(relationship).delete(relationship.requiredSymbolicName).build()
        return executeStatement(statement)
    }

    private fun executeStatement(statement: Statement): Mono<Void> {
        return neo4jClient.query(Renderer.getDefaultRenderer().render(statement)).bindAll(statement.parameters).run()
            .then()
    }

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

    private fun getNodesToSave(node: Node): Collection<Node> {
        val nodeDefinition = nodeDefinitionCollection.getNodeDefinition(node::class)
        return nodeDefinition.relationshipDefinitions.values.flatMap {
            it.getRelatedNodesToSave(node)
        }
    }
}
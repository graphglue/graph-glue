package de.graphglue.neo4j.repositories

import de.graphglue.graphql.execution.definition.NodeDefinition
import de.graphglue.graphql.execution.definition.NodeDefinitionCollection
import de.graphglue.graphql.execution.definition.RelationshipDefinition
import de.graphglue.model.Node
import org.neo4j.cypherdsl.core.Cypher
import org.neo4j.cypherdsl.core.Relationship
import org.neo4j.cypherdsl.core.Statement
import org.neo4j.cypherdsl.core.renderer.Renderer
import org.reactivestreams.Publisher
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.ReactiveNeo4jOperations
import org.springframework.data.neo4j.repository.support.Neo4jEntityInformation
import org.springframework.data.neo4j.repository.support.SimpleReactiveNeo4jRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class SimpleReactiveGraphglueRepository<T, ID>(
    private val neo4jOperations: ReactiveNeo4jOperations,
    entityInformation: Neo4jEntityInformation<T, ID>,
    private val nodeDefinitionCollection: NodeDefinitionCollection,
    private val neo4jClient: ReactiveNeo4jClient
) : SimpleReactiveNeo4jRepository<T, ID>(neo4jOperations, entityInformation) {
    override fun <S : T> save(entity: S): Mono<S> {
        return if (entity is Node) {
            saveNode(entity)
        } else {
            super.save(entity)
        }
    }

    private fun <S : T> saveNode(entity: Node): Mono<S> {
        val nodesToSave = getNodesToSaveRecursive(entity)
        return Flux.fromIterable(nodesToSave).flatMap { nodeToSave ->
            neo4jOperations.save(nodeToSave).map { nodeToSave to it }
        }.collectList().flatMap { saveResult ->
            val nodeIdLookup = saveResult.associate { (nodeToSave, savedNode) ->
                nodeToSave to savedNode.rawId!!
            }
            @Suppress("UNCHECKED_CAST") val resultNode = saveResult.toMap()[entity]!! as S
            Flux.fromIterable(nodeIdLookup.keys).flatMap { nodeToSave ->
                val nodeDefinition = nodeDefinitionCollection.getNodeDefinition(nodeToSave::class)
                saveAllRelationships(nodeDefinition, nodeToSave, nodeIdLookup)
            }.then(Mono.just(resultNode))
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
        val relationship = generateRelationship(relationshipDefinition, rootNodeId, propertyNode)
        val statement = Cypher.match(relationship).merge(relationship).build()
        return executeStatement(statement)
    }

    private fun deleteRelationship(
        relationshipDefinition: RelationshipDefinition, rootNodeId: String, propertyNode: org.neo4j.cypherdsl.core.Node
    ): Mono<Void> {
        val relationship = generateRelationship(relationshipDefinition, rootNodeId, propertyNode)
        val statement = Cypher.match(relationship).delete(relationship).build()
        return executeStatement(statement)
    }

    private fun executeStatement(statement: Statement): Mono<Void> {
        return neo4jClient.query(Renderer.getDefaultRenderer().render(statement)).bindAll(statement.parameters).run()
            .then()
    }

    private fun generateRelationship(
        relationshipDefinition: RelationshipDefinition, rootNodeId: String, propertyNode: org.neo4j.cypherdsl.core.Node
    ): Relationship {
        val idParameter = Cypher.anonParameter(rootNodeId)
        val rootNode = Cypher.anyNode().withProperties(mapOf("id" to idParameter))
        return relationshipDefinition.generateRelationship(rootNode, propertyNode)
    }

    private fun getNodesToSaveRecursive(node: Node): Set<Node> {
        val nodesToSave = HashSet<Node>()
        val nodesToVisit = hashSetOf(node)
        while (nodesToVisit.isNotEmpty()) {
            val nextNode = nodesToVisit.first()
            nodesToSave.add(nextNode)
            val nodesToAdd = getNodesToSave(nextNode).filter { !nodesToSave.contains(it) }
            nodesToVisit.addAll(nodesToAdd)
        }
        return nodesToSave
    }

    private fun getNodesToSave(node: Node): Collection<Node> {
        val nodeDefinition = nodeDefinitionCollection.getNodeDefinition(node::class)
        return nodeDefinition.relationshipDefinitions.values.flatMap {
            it.getRelatedNodesToSave(node)
        }
    }

    override fun <S : T> saveAll(entities: MutableIterable<S>): Flux<S> {
        return Flux.fromIterable(entities).flatMap(this::save)
    }

    override fun <S : T> saveAll(entityStream: Publisher<S>): Flux<S> {
        return Flux.from(entityStream).flatMap(this::save)
    }
}
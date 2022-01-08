package de.graphglue.model

import de.graphglue.neo4j.execution.NodeQuery
import org.springframework.stereotype.Service

@Service
class NodeService {
    fun findAll(nodeQuery: NodeQuery): List<Node> {
        TODO()
    }
}
package com.nkcoding.graphglue.model

import com.nkcoding.graphglue.graphql.execution.NodeQuery
import org.springframework.stereotype.Service

@Service
class NodeService {
    fun findAll(nodeQuery: NodeQuery): List<Node> {
        TODO()
    }
}
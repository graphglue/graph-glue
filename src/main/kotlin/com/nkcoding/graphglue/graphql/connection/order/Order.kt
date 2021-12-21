package com.nkcoding.graphglue.graphql.connection.order

import com.fasterxml.jackson.databind.ObjectMapper
import com.nkcoding.graphglue.model.Node
import java.util.*

class Order<in T : Node>(val direction: OrderDirection, val field: OrderField<T>) {
    fun generateCursor(node: T, objectMapper: ObjectMapper): String {
        val properties = this.field.parts.associate { it.property.name to it.getValue(node) }
        return Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(properties))
    }

    fun parseCursor(cursor: String, objectMapper: ObjectMapper): Map<String, Any?> {
        val decoded = Base64.getDecoder().decode(cursor)
        val result = objectMapper.readValue<Map<String, Any?>>(decoded, objectMapper.constructType(Map::class.java))
        if (result.keys.toSet() != field.parts.map { it.property.name }.toSet()) {
            throw IllegalArgumentException("Invalid cursor: $cursor")
        }
        return result
    }
}
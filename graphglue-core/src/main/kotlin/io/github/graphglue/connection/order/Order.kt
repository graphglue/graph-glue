package io.github.graphglue.connection.order

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.graphglue.model.Node
import java.util.*

/**
 * Order defined by an [OrderField] and an [OrderDirection]
 *
 * @param T the type of [Node] to which this order is applied
 * @param fields defines how to order, e.g. by which properties of the node
 */
class Order<in T : Node>(val fields: List<OrderField<T>>) {

    /**
     * Generates a cursor for a [node], e.g. a unique identifier for the provided node based on the properties
     * specified by [fields]
     *
     * @param node the [Node] to generate the cursor for
     * @param objectMapper necessary for JSON encoding
     * @return the cursor as base64 encoded JSON
     */
    fun generateCursor(node: T, objectMapper: ObjectMapper): String {
        val properties = this.fields.associate { it.part.name to node.orderFields!![it.part.name] }
        return Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(properties))
    }

    /**
     * Parses a cursor generated by [generateCursor] into a map from [OrderPart] name to value provided in the cursor
     * Can be used for filtering in the database, e.g. return only [Node]s which are after / before the cursor
     *
     * @param cursor the base64 encoded cursor
     * @param objectMapper necessary for JSON decoding
     * @throws IllegalArgumentException if the cursor is invalid
     */
    fun parseCursor(cursor: String, objectMapper: ObjectMapper): Map<String, Any?> {
        val decoded = Base64.getDecoder().decode(cursor)
        val result = objectMapper.readValue<Map<String, Any?>>(decoded, objectMapper.constructType(Map::class.java))
        if (result.keys.toSet() != fields.map { it.part.name }.toSet()) {
            throw IllegalArgumentException("Invalid cursor: $cursor")
        }
        return result
    }
}
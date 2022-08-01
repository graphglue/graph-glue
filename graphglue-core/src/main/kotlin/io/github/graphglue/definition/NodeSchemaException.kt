package io.github.graphglue.definition

/**
 * Thrown to indicate that a node relation was wrongly declared
 *
 * @param message the message of the exception
 */
class NodeSchemaException(message: String) : RuntimeException(message)
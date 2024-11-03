package io.github.graphglue.model.property

import io.github.graphglue.definition.FieldDefinition
import io.github.graphglue.model.Node
import kotlin.reflect.KProperty1

/**
 *  @param parent the node which hosts this property
 *  @param property the property on the class
 *  @param T query result data type
 */
abstract class PropertyDelegate<T>(
    protected val parent: Node,
    protected val property: KProperty1<*, *>
) {

    /**
     * Called to register a database query result
     * Adds the result to the cache
     * Can be overridden to add custom behavior (super should be called in this case)
     *
     * @param queryResult the result of the query
     */
    abstract fun registerQueryResult(fieldDefinition: FieldDefinition,  queryResult: T)

}
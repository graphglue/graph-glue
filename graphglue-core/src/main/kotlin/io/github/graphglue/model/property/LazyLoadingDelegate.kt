package io.github.graphglue.model.property

import io.github.graphglue.model.Node

/**
 * Delegate which can be called to get the loaded property
 *
 * @param T the type of Node stored in this property
 * @param R the type of property
 */
interface LazyLoadingDelegate<T: Node?, R> {
    /**
     * Gets the loaded property
     */
    suspend operator fun invoke(): R
}
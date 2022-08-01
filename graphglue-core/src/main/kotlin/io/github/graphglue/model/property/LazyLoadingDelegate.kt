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
     *
     * @param cache used to load nodes from, if provided, not loading deleted nodes
     */
    suspend operator fun invoke(cache: NodeCache? = null): R
}
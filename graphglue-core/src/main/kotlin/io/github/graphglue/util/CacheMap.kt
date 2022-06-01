package io.github.graphglue.util

import java.util.function.Consumer
import java.util.function.Function

/**
 * Cache which can be used to cache and compute entities
 * @param K the type of the keys under which cached entities are saved
 * @param V the type of the cached entities
 * @param backingMap the map which is used to cache the entities, defaults to a Hashmap
 */
class CacheMap<K, V>(private val backingMap: MutableMap<K, V> = HashMap()) : Map<K, V> by backingMap {

    /**
     * set of entities which are currently computed using a callback
     */
    private val currentlyComputing = mutableSetOf<K>()

    /**
     * Gets the entity in cache under the specified `key`
     * If no cache entry is found, the `mappingFunction` is used to create a new entity, which
     * is saved to the cache and returned.
     * Throws an exception if the entity under the provided key is currently computed
     *
     * @param key the key for the cache entity
     * @param mappingFunction used to create the entity if not found in cache
     * @return the cache entity associated with the provided key
     * @throws IllegalArgumentException if the entity under the provided key is currently computed
     */
    fun computeIfAbsent(key: K, mappingFunction: Function<in K, out V>): V {
        if (key in currentlyComputing) {
            throw IllegalArgumentException("Already computing value for key $key")
        }
        return if (key in this) {
            this.getValue(key)
        } else {
            currentlyComputing.add(key)
            val result = mappingFunction.apply(key)
            backingMap[key] = result
            currentlyComputing.remove(key)
            result
        }
    }

    /**
     * Gets the entity in cache under the specified `key`
     * If no cache entry is found, the `mappingFunction` is used to create a new entity, which
     * is saved to the cache and returned.
     * If the value is currently computed, returns `alternativeIfComputing`
     *
     * @param key the key for the cache entity
     * @param alternativeIfComputing alternative return value if value is currently in computation
     * @param mappingFunction used to create the entity if not found in cache
     * @return the cache entity associated with the provided key
     * @throws IllegalArgumentException if the entity under the provided key is currently computed
     */
    fun computeIfAbsent(key: K, alternativeIfComputing: V, mappingFunction: Function<in K, out V>): V {
        return if (key in currentlyComputing) {
            alternativeIfComputing
        } else {
            computeIfAbsent(key, mappingFunction)
        }
    }

    fun putAndInitIfAbsent(key: K, value: V, initFunction: Consumer<V>): V {
        if (key in currentlyComputing) {
            throw IllegalArgumentException("Already computing value for key $key")
        }
        return if (key in this) {
            this.getValue(key)
        } else {
            backingMap[key] = value
            initFunction.accept(value)
            value
        }
    }
}
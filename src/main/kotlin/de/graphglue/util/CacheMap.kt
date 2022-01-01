package de.graphglue.util

import java.util.function.Function

class CacheMap<K, V>(private val backingMap: MutableMap<K, V> = HashMap()) : Map<K, V> by backingMap {

    private val currentlyComputing = mutableSetOf<K>()

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

    fun computeIfAbsent(key: K, alternativeIfComputing: V, mappingFunction: Function<in K, out V>): V {
        return if (key in currentlyComputing) {
            alternativeIfComputing
        } else {
            computeIfAbsent(key, mappingFunction)
        }
    }
}
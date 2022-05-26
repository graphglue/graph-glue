package io.github.graphglue.util

/**
 * Helper function to iterate over a graph
 * The [Collection] on which it is called is treated as the set of starting nodes
 * [action] is called for each node in the graph exactly once, and returns a set of nodes to iterate over.
 * This returned set of nodes may contain duplicates, or nodes which have already been iterated over.
 *
 * @param T the type of node, must properly implement `equals` and `hashcode`
 * @param action invoked for each node of the graph, returns the next nodes
 */
fun <T> Collection<T>.iterateGraph(action: (T) -> Collection<T>) {
    val checkedNodes = this.toMutableSet()
    val nodesToCheck = ArrayDeque(checkedNodes)
    while (nodesToCheck.isNotEmpty()) {
        val toCheck = nodesToCheck.removeFirst()
        val newNodes = action(toCheck)
        for (newNode in newNodes) {
            if (checkedNodes.add(newNode)) {
                nodesToCheck += newNode
            }
        }
    }
}
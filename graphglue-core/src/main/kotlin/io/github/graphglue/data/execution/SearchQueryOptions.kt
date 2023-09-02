package io.github.graphglue.data.execution

/**
 * Defines how a [SearchQuery] fetches data
 *
 * @param filters filters to apply to the search
 * @param query the search query
 * @param first the maximum number of results to return
 */
data class SearchQueryOptions(
    val filters: List<CypherConditionGenerator> = emptyList(),
    val query: String,
    val first: Int
) {

    init {
        if (first < 0) {
            throw IllegalArgumentException("first must be >= 0")
        }
    }

}
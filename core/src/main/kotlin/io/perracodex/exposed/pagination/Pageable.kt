/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

package io.perracodex.exposed.pagination

import kotlinx.serialization.Serializable

/**
 * Encapsulates the parameters required to request a specific page of data from a paginated dataset.
 *
 * @property page The zero-based index of the page to retrieve.
 * @property position Optional absolute zero-based starting position within the full result set.
 * @property size The maximum number of elements to include in a single page. `0` to return all elements without pagination.
 * @property sort An optional list of [PageSort] directives to order the results.
 */
@Serializable
public data class Pageable(
    val page: Int,
    val position: Int?,
    val size: Int,
    val sort: List<PageSort>? = null
) {
    init {
        require(value = (page >= 0)) { "Page index must be >= 0." }
        require(value = (position == null || position >= 0)) { "Position must be >= 0 when provided." }
        require(value = (size >= 0)) { "Page size must be >= 0. (0 means all elements)." }
    }

    /**
     * Sorting direction.
     */
    public enum class PageDirection {
        /** Ascending sorting direction. */
        ASC,

        /** Descending sorting direction. */
        DESC
    }

    /**
     * Specifies the sorting order for a particular field within the dataset.
     *
     * @property table Optional name of the table that the field belongs to. Used to find ambiguities in multi-table queries.
     * @property field The name of the field to sort by.
     * @property direction The [PageDirection] in which to sort the field.
     */
    @Serializable
    public data class PageSort(
        val table: String? = null,
        val field: String,
        val direction: PageDirection
    ) {
        init {
            require(value = field.isNotBlank()) { "The sorting field name must not be blank." }
            require(value = table == null || table.isNotBlank()) { "The table name must not be an empty string if provided." }
        }
    }
}

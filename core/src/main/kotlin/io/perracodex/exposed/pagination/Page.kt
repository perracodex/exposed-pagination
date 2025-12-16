/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

package io.perracodex.exposed.pagination

import kotlinx.serialization.Serializable

/**
 * Represents a single page of results within a paginated dataset,
 * encapsulating both the data content for a concrete page and metadata
 * about the pagination state.
 *
 * @param T The type of elements contained within the page.
 * @property details Metadata providing [PageDetails] about the pagination state, such as total pages and current page index.
 * @property content The list of elements of type [T] contained in the page.
 */
@Serializable
public data class Page<out T : Any>(
    val details: PageDetails,
    val content: List<T>,
) {
    /**
     * Contains metadata describing the state and configuration of a paginated [Page].
     *
     * @property totalPages The total number of pages available based on the pagination parameters.
     * @property pageIndex The zero-based index of the current page.
     * @property position The absolute zero-based starting position (offset).
     * @property totalElements The aggregate number of elements across all pages.
     * @property elementsPerPage The maximum number of elements that can be contained in a single page.
     * @property elementsInPage The actual number of elements present in the current page.
     * @property isFirst Indicates whether the current page is the first one.
     * @property isLast Indicates whether the current page is the last one.
     * @property hasNext Indicates if there is a subsequent page available after the current one.
     * @property hasPrevious Indicates if there is a preceding page before the current one.
     * @property isOverflow Indicates whether the requested page index exceeds the total number of available pages.
     * @property sort The sorting criteria applied to the elements within the page, if any.
     */
    @Serializable
    public data class PageDetails(
        val totalPages: Int,
        val pageIndex: Int,
        val position: Int,
        val totalElements: Int,
        val elementsPerPage: Int,
        val elementsInPage: Int,
        val isFirst: Boolean,
        val isLast: Boolean,
        val hasNext: Boolean,
        val hasPrevious: Boolean,
        val isOverflow: Boolean,
        val sort: List<Pageable.PageSort>?,
    )

    public companion object {
        /**
         *  Factory method to construct a new [Page] instance with the specified content and pagination parameters.
         *
         * @param content The list of elements to include in the current page.
         * @param totalElements The total count of elements across all pages.
         * @param pageable The [Pageable] settings used to retrieve the content. `null` if no pagination was applied.
         * @return A [Page] containing the specified [content] and corresponding pagination [PageDetails].
         */
        public fun <T : Any> build(content: List<T>, totalElements: Int, pageable: Pageable?): Page<T> {
            val pageSize: Int = resolvePageSize(pageable = pageable, totalElements = totalElements)
            val totalPages: Int = calculateTotalPages(totalElements = totalElements, pageSize = pageSize)
            val (pageIndex: Int, positionIndex: Int) = determineIndexes(pageable = pageable, pageSize = pageSize)

            // Adjust pagination state based on total pages and content availability.
            val elementsInPage: Int = content.size
            val isFirst: Boolean = (totalPages == 0) || (pageIndex == 0)
            val isLast: Boolean = (totalPages == 0) || (pageIndex >= totalPages - 1)
            val hasNext: Boolean = (pageIndex < totalPages - 1) && (totalPages > 0)
            val hasPrevious: Boolean = (pageIndex > 0) && (totalPages > 0)

            // True if the requested page or position exceeds the available elements.
            val isOverflow: Boolean = checkOverflow(
                pageable = pageable,
                totalElements = totalElements,
                totalPages = totalPages,
                pageIndex = pageIndex,
                positionIndex = positionIndex
            )

            // Construct the Page object with the determined states.
            return Page(
                details = PageDetails(
                    totalPages = totalPages,
                    pageIndex = pageIndex,
                    position = positionIndex,
                    totalElements = totalElements,
                    elementsPerPage = pageSize,
                    elementsInPage = elementsInPage,
                    isFirst = isFirst,
                    isLast = isLast,
                    hasNext = hasNext,
                    hasPrevious = hasPrevious,
                    isOverflow = isOverflow,
                    sort = pageable?.sort
                ),
                content = content
            )
        }

        /**
         * Resolves the effective page size based on the requested pageable.
         *
         * @param pageable The pagination request.
         * @param totalElements The total number of elements available.
         * @return A valid page size greater than zero, or defaults to [totalElements].
         */
        private fun resolvePageSize(pageable: Pageable?, totalElements: Int): Int {
            val requested: Int? = pageable?.size
            return if (requested != null && requested > 0) {
                requested
            } else {
                totalElements
            }
        }

        /**
         * Calculates the total number of pages.
         *
         * @param totalElements The total number of elements available.
         * @param pageSize The number of elements per page.
         * @return Total pages, or 0 if no elements are available.
         */
        private fun calculateTotalPages(totalElements: Int, pageSize: Int): Int {
            return if (totalElements > 0 && pageSize > 0) {
                ((totalElements + pageSize - 1) / pageSize).coerceAtLeast(minimumValue = 1)
            } else {
                0
            }
        }

        /**
         * Determines the page index and absolute position index based on the pageable request.
         *
         * @param pageable The pagination request.
         * @param pageSize The number of elements per page.
         * @return A pair of (pageIndex, positionIndex).
         */
        private fun determineIndexes(pageable: Pageable?, pageSize: Int): Pair<Int, Int> {
            return when {
                pageable == null -> 0 to 0
                pageSize <= 0 -> 0 to 0
                pageable.position != null -> (pageable.position / pageSize) to pageable.position
                else -> pageable.page to (pageable.page * pageSize)
            }
        }

        /**
         * Checks whether the requested page or position exceeds the available elements.
         *
         * @param pageable The pagination request.
         * @param totalElements The total number of elements available.
         * @param totalPages The total number of pages.
         * @param pageIndex The current page index.
         * @param positionIndex The absolute position index.
         * @return True if the request exceeds available elements, false otherwise.
         */
        private fun checkOverflow(
            pageable: Pageable?,
            totalElements: Int,
            totalPages: Int,
            pageIndex: Int,
            positionIndex: Int
        ): Boolean {
            return if (pageable?.position != null) {
                (totalElements in 1..positionIndex) || (totalElements <= 0 && positionIndex > 0)
            } else {
                (totalPages in 1..pageIndex) || (totalPages <= 0 && pageIndex > 0)
            }
        }

        /**
         * Factory method to create an empty [Page] instance with no content.
         * Useful for scenarios where a query returns no results but pagination metadata is still required.
         *
         * @param pageable The [Pageable] settings that were applied to the query, or `null` if no pagination was used.
         * @return An empty [Page] with appropriate pagination [PageDetails].
         */
        public fun <T : Any> empty(pageable: Pageable?): Page<T> {
            return build(content = emptyList(), totalElements = 0, pageable = pageable)
        }
    }
}

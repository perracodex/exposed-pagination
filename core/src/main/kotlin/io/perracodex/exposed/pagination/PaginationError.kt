/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

package io.perracodex.exposed.pagination

/**
 * Represents various errors that can occur during pagination operations.
 *
 * @property errorCode A unique identifier for the specific type of pagination error.
 * @property description A clear and concise message describing the error.
 * @property reason An optional detailed explanation providing additional context for the error.
 * @property cause The underlying exception that triggered this pagination error, if any.
 */
public sealed class PaginationError(
    public val errorCode: String,
    public val description: String,
    public val reason: String? = null,
    cause: Throwable? = null
) : Exception(description, cause) {
    /**
     * Indicates that a provided sorting field is ambiguous because it exists in multiple tables.
     *
     * This error is thrown when the sorting directive does not specify a table prefix for a field
     * that exists in more than one table involved in the query, leading to ambiguity in sorting.
     *
     * @param sort The [Pageable.PageSort] directive that caused the ambiguity.
     * @param reason A detailed explanation of why the sort field is considered ambiguous.
     */
    public class AmbiguousSortField(sort: Pageable.PageSort, reason: String) : PaginationError(
        errorCode = "AMBIGUOUS_SORT_FIELD",
        description = "Detected ambiguous field: ${sort.field}",
        reason = reason
    )

    /**
     * Indicates that only one of the pagination parameters ('page' or 'size') was provided.
     *
     * Both 'page' and 'size' must be present to perform pagination.
     * Providing only one leads to an incomplete pagination request.
     */
    public class InvalidPageablePair : PaginationError(
        errorCode = "INVALID_PAGEABLE_PAIR",
        description = "Page attributes mismatch. Use either 'page'+'size' or 'position'+'size' (mutually exclusive), or none.",
    )

    /**
     * Indicates that an invalid sorting direction was specified in a sort directive.
     *
     * This error is thrown when the direction specified in a sorting parameter is unknown.
     *
     * @param direction The invalid sort direction that was provided.
     */
    public class InvalidOrderDirection(direction: String) : PaginationError(
        errorCode = "INVALID_ORDER_DIRECTION",
        description = "Ordering sort direction is invalid. Received: '$direction'",
    )

    /**
     * Indicates that a sort directive provided is not recognized.
     *
     * This error is thrown when trying to sort by a field that is not part of the query.
     *
     * @param sort The [Pageable.PageSort] directive that is invalid.
     * @param reason A detailed explanation of why the sort directive is considered invalid.
     */
    public class InvalidSortDirective(sort: Pageable.PageSort, reason: String) : PaginationError(
        errorCode = "INVALID_SORT_DIRECTIVE",
        description = "Unexpected sort directive: $sort",
        reason = reason
    )

    /**
     * Indicates that a sort directive was provided without specifying a field name.
     *
     * This error is thrown when a sort parameter is present but does not include a field to sort by.
     */
    public class MissingSortDirective : PaginationError(
        errorCode = "MISSING_SORT_DIRECTIVE",
        description = "Must specify a sort field name.",
    )
}

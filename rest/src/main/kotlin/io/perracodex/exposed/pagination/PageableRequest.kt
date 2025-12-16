/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

package io.perracodex.exposed.pagination

import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.perracodex.exposed.utils.PageableKeys
import io.perracodex.exposed.utils.SortParameterParser

/**
 * Creates a [Pageable] instance by extracting pagination and sorting parameters from the [ApplicationCall] request.
 *
 * **Pagination Parameters:**
 * - **`page`**: The zero-based index of the requested page.
 * - **`size`**: The number of elements per page. `0` if all elements should be returned without pagination.
 * - **`position`**: The zero-based index (absolute position) of the requested element.
 *
 * Rules:
 * - You must provide either `page` and `size`, or `position` and `size`, or none.
 *   Do not provide both `page` and `position`.
 *
 * **Sorting Parameters:**
 * - **`sort`**: A list of strings defining the sorting criteria. Format `"fieldName,direction"`, where:
 *      - **`fieldName`**: The name of the field to sort by.
 *      - **`direction`** (optional): The sorting direction, If omitted, ascending order is assumed.
 *      - Multiple `sort` parameters can be provided to apply multi-field sorting.
 *
 * **Sorting Logic for Multiple Tables:**
 * - To avoid ambiguity in queries involving multiple tables, sorting fields can be prefixed with the table name: `"tableName.fieldName"`.
 * - If a sorting field includes a table prefix (e.g., `"employees.firstName"`), the sort is applied specifically to that table's field.
 * - If a field name without a table prefix is used, and it causes ambiguity due to duplicate field names across tables in the
 *   target query, an exception is thrown. It is recommended to always use table-prefixed field names in such scenarios.
 *
 * **Sample Usage:**
 *
 * ```
 * fun Route.findAllEmployees() {
 *     get("v1/employees") {
 *          // Get the pagination directives, (if any).
 *         val pageable: Pageable? = call.getPageable()
 *         // Utilize the pageable object to retrieve a page of employees.
 *         val employees: Page<Employee> = EmployeeService.findAll(pageable)
 *         // Respond with a Page object.
 *         call.respond(status = HttpStatusCode.OK, message = employees)
 *     }
 * }
 * ```
 *
 * @return A [Pageable] instance containing pagination information extracted from the request. `null` if no parameters were provided.
 * @throws PaginationError.InvalidPageablePair If only one of the pagination parameters (`page` or `size`) is provided.
 * @throws PaginationError.InvalidOrderDirection If any provided sort direction is invalid.
 *
 * @see [Pageable]
 * @see [PaginationError]
 */
public fun ApplicationCall.getPageable(): Pageable? {
    val parameters: Parameters = request.queryParameters
    val pageIndex: Int? = parameters[PageableKeys.PAGE.key]?.toIntOrNull()
    val pageSize: Int? = parameters[PageableKeys.SIZE.key]?.toIntOrNull()
    val positionIndex: Int? = parameters[PageableKeys.POSITION.key]?.toIntOrNull()

    // Validate combinations: require (page & size) together OR (position & size) together, not both.
    val pagePair: Boolean = (pageIndex != null) && (pageSize != null)
    val positionPair: Boolean = (positionIndex != null) && (pageSize != null)
    val bothPairsPresent: Boolean = pagePair && positionPair
    val noPairsButSizePresent: Boolean = !pagePair && !positionPair && (pageSize != null)
    if (bothPairsPresent || noPairsButSizePresent) {
        throw PaginationError.InvalidPageablePair()
    }

    // Retrieve the 'sort' parameters. Each can contain a field name and a sort direction.
    val sortParameters: List<String>? = parameters.getAll(name = PageableKeys.SORT.key)

    // If no parameters are provided, means no pagination is requested.
    if ((pageIndex == null) && (positionIndex == null) && sortParameters.isNullOrEmpty()) {
        return null
    }

    // Parse sorting parameters into a list of Sort directives.
    val sort: List<Pageable.PageSort>? = sortParameters?.let {
        SortParameterParser.getSortDirectives(sortParameters = sortParameters)
    }

    return Pageable(
        page = pageIndex ?: 0,
        size = pageSize ?: 0,
        sort = sort,
        position = positionIndex
    )
}

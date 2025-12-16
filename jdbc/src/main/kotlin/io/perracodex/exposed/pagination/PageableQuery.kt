/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

package io.perracodex.exposed.pagination

import io.perracodex.exposed.utils.QuerySorter
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select

/**
 * Retrieves a page from this [Query] based on the specified [pageable] parameters.
 * The query instance is **not** modified.
 *
 * If [pageable] is `null`, or the defined page size is `0`, the entire result set is retrieved without any pagination,
 * and a [Page] containing all domain models is returned.
 *
 * #### Usage:
 * ```
 * // Note that the actual model mapping has still to be done by the caller.
 * // See the `MapModel` interface for more information.
 *
 * fun findProduct(categoryId: Uuid, pageable: Pageable?): Page<Product> {
 *     return transaction {
 *         ProductTable
 *             .selectAll()
 *             .where { ProductTable.categoryId eq categoryId }
 *             .paginate(pageable = pageable, map = Product)
 *     }
 * }
 * ```
 *
 * @param pageable Optional [Pageable] containing pagination and sorting information. If `null`, no pagination is applied.
 * @param map Implementation of [MapModel] used to map [ResultRow] instances into domain models of type [T].
 * @return A [Page] containing the list of mapped domain models and associated pagination metadata.
 *
 * @see [Page]
 * @see [Pageable]
 * @see [MapModel]
 * @see [Query.paginate]
 */
public fun <T : Any> Query.paginate(pageable: Pageable?, map: MapModel<T>): Page<T> {
    // Calculate total elements before pagination.
    val totalElements: Long = this.count()
    if (totalElements == 0L) {
        return Page.empty(pageable = pageable)
    }

    // Apply pagination and capture the modified query.
    val paginatedQuery: Query = this.copy().paginate(pageable = pageable)

    // Map each row to the domain model.
    val content: List<T> = paginatedQuery.map { resultRow ->
        map.from(row = resultRow)
    }

    return Page.build(
        content = content,
        totalElements = totalElements.toInt(),
        pageable = pageable
    )
}

/**
 * Retrieves a page from this [Query] based on the specified [pageable] parameters.
 * The query instance is **not** modified.
 *
 * This is meant to be used by 1 -> N relationships where the query returns multiple rows per top-level entity.
 *
 * When performing queries that join a main entity with related entities,
 * it's essential to manage pagination in a way that accurately reflects the number of unique top-level entities.
 *
 * If [pageable] is `null`, or the defined page size is `0`, the entire result set is retrieved without any pagination,
 * and a [Page] containing all grouped domain models is returned.
 *
 * #### Usage:
 * ```
 * // Note that the actual model mapping has still to be done by the caller.
 * // See the `MapModel` interface for more information.
 *
 * fun findAll(pageable: Pageable?): Page<Employee> {
 *     return transaction {
 *         EmployeeTable
 *             .leftJoin(ContactTable)
 *             .leftJoin(EmploymentTable)
 *             .selectAll()
 *             .paginate(
 *                 pageable = pageable,
 *                 map = Employee,
 *                 groupBy = EmployeeTable.id
 *             )
 *     }
 * }
 * ```
 *
 * @param pageable Optional [Pageable] containing pagination and sorting information. If `null`, no pagination is applied.
 * @param map Implementation of [MapModel] used to map [ResultRow] instances into domain models of type [T].
 * @param groupBy The column used to group the top-entity results.
 * @return A [Page] containing the list of mapped domain models and associated pagination metadata.
 *
 * @see [Page]
 * @see [Pageable]
 * @see [MapModel]
 * @see [Query.paginate]
 */
public fun <T : Any, K> Query.paginate(
    pageable: Pageable?,
    map: MapModel<T>,
    groupBy: Column<K>
): Page<T> {
    // Calculate the total number of top-level entities (distinct values of the groupBy column).
    // For 1 -> N relationships, this ensures we count only the top-level entities, not all rows.
    val totalElements: Long = this.copy()
        .adjustSelect { select(groupBy.countDistinct()) }
        .first()[groupBy.countDistinct()]
    if (totalElements == 0L) {
        return Page.empty(pageable = pageable)
    }

    // Construct the paginated keys query for the top-level entities.
    val paginatedKeys: Query = this.copy()
        .adjustSelect { select(groupBy) }
        .groupBy(groupBy)
        .paginate(pageable = pageable)

    // Fetch the records that correspond to the paginated keys.
    val records: List<ResultRow> = this.copy()
        .andWhere { groupBy inSubQuery paginatedKeys }
        .toList()
    if (records.isEmpty()) {
        return Page.empty(pageable = pageable)
    }

    // Map each group of rows to the corresponding domain model.
    val content: List<T> = records
        .groupBy { it[groupBy] }
        .mapNotNull { (_, groupRows) ->
            map.from(rows = groupRows)
        }

    return Page.build(
        content = content,
        totalElements = totalElements.toInt(),
        pageable = pageable
    )
}

/**
 * Applies pagination directives to this [Query] based on the provided [pageable] parameters.
 * The query instance is modified in-place.
 *
 * This extension function modifies the query in the following ways:
 * 1. **Sorting:** If [pageable] includes sorting directives, they are applied to the [Query].
 * 2. **Limiting and offset:**
 *    - If [pageable.position] is defined, it is used as the absolute zero-based starting position (offset).
 *    - Otherwise, the offset is calculated from the page number as `page * size`.
 *
 * If [pageable] is `null`, or the defined page size is `0`, the entire result set is retrieved without any pagination,
 * and a [Page] containing all domain models is returned.
 *
 * @param pageable Optional [Pageable] containing pagination and sorting information. If `null`, no pagination is applied.
 * @return The modified [Query] with pagination and sorting applied if [pageable] is provided;
 * otherwise, the original [Query] is returned unaltered.
 *
 * @see [Pageable]
 */
public fun Query.paginate(pageable: Pageable?): Query {
    pageable?.let {
        pageable.sort?.let { sortDirectives ->
            QuerySorter.applyOrder(query = this, sortDirectives = sortDirectives)
        }

        if (pageable.size > 0) {
            val startIndex: Long = pageable.position?.toLong() ?: (pageable.page.toLong() * pageable.size.toLong())
            this.limit(count = pageable.size)
                .offset(start = startIndex)
        }
    }

    return this
}

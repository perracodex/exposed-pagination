/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

package io.perracodex.exposed.utils

import io.perracodex.exposed.pagination.Pageable
import io.perracodex.exposed.pagination.PaginationError
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.full.memberProperties

/**
 * Handles the determination and application of column-based ordering
 * for database [Query] instances.
 */
internal object QuerySorter {
    private val tracer = Tracer<QuerySorter>()

    /**
     * Cache storing column references.
     * Used to optimize the reflection process of finding table columns.
     *
     * @see generateCacheKey
     */
    private val columnCache: MutableMap<String, Column<*>> = ConcurrentHashMap()

    /**
     * Applies ordering to a query based on the provided list of [Pageable.Sort] directives.
     *
     * @param query The query to apply ordering to.
     * @param sortDirectives The list of sorting directives to apply to the query.
     */
    fun applyOrder(query: Query, sortDirectives: List<Pageable.Sort>) {
        if (sortDirectives.isEmpty()) {
            return
        }

        val queryTables: List<Table> = query.targets.distinct()

        sortDirectives.forEach { sort ->
            // Get the list of query tables to resolve the field from the sort directive.
            val targetTables: List<Table> = findTargetTables(queryTables = queryTables, sort = sort)

            // Retrieve the column from the target tables.
            val key: String = generateCacheKey(queryTables = queryTables, sort = sort)
            val column: Column<*> = getColumn(
                key = key,
                sort = sort,
                targets = targetTables
            )

            // Apply the sorting order to the query based on the direction
            // specified in the Pageable.
            val sortOrder: SortOrder = when (sort.direction) {
                Pageable.Direction.ASC -> SortOrder.ASC
                Pageable.Direction.DESC -> SortOrder.DESC
            }
            query.orderBy(column to sortOrder)
        }
    }

    /**
     * Filters the list of query tables needed to resolve the field from the sort directive.
     *
     * If a table name is provided in the sort directive, only tables with a matching name are returned.
     * If no table name is provided, all query tables are returned as potential targets.
     *
     * @param queryTables The list of tables involved in the query.
     * @param sort The sorting directive containing the table name.
     * @return The list of target tables based on the sort directive.
     */
    private fun findTargetTables(queryTables: List<Table>, sort: Pageable.Sort): List<Table> {
        return sort.table?.let { tableName ->
            queryTables.filter { table ->
                table.tableName.equals(other = tableName, ignoreCase = true)
            }.distinct().takeIf { tables ->
                tables.isNotEmpty()
            } ?: throw PaginationError.InvalidSortDirective(
                sort = sort,
                reason = "'$tableName' is not recognized as part of the Query tables."
            )
        } ?: queryTables
    }

    /**
     * Attempts to retrieve a column, first from the cache, or of not found, try to resolve
     * it via reflection from the given list of table [targets] and cache it.
     *
     * @param key The cache key to retrieve/store the column reference.
     * @param sort The sorting directive containing the field name.
     * @param targets A list of tables to search for the column.
     * @return The found Column reference, or null if not found.
     */
    private fun getColumn(key: String, sort: Pageable.Sort, targets: List<Table>): Column<*> {
        // Check if the column is already cached, and return it if found.
        columnCache[key]?.let { column ->
            return column
        }

        // Attempt to resolve the column from the target tables, which are part of the query.
        val columns: List<Column<*>> = targets.asSequence().flatMap { table ->
            resolveTableColumn(table = table, fieldName = sort.field)
        }.distinct().toList()

        if (columns.isEmpty()) {
            // If no columns are found, it implies the field is not part of the query tables.
            throw PaginationError.InvalidSortDirective(sort = sort, reason = "Field not found in query tables.")
        } else if (columns.size > 1) {
            // If multiple columns are found, it implies ambiguity between tables.
            val reason = "'${sort.field}' found in: ${columns.joinToString { it.table.tableName }}"
            throw PaginationError.AmbiguousSortField(sort = sort, reason = reason)
        }

        // Cache the column and return it.
        val column: Column<*> = columns.single()
        columnCache[key] = column
        return column
    }

    /**
     * Searches for a column in the given [table] by matching its property names
     * with the given [fieldName].
     *
     * @param table The table to search for the column.
     * @param fieldName The name of the field representing the column.
     * @return List of Column references from the table that match the field name.
     */
    private fun resolveTableColumn(table: Table, fieldName: String): List<Column<*>> {
        return table::class.memberProperties.filter { property ->
            // Look for a property in the table class that matches the field name and is a Column type.
            property.returnType.classifier == Column::class &&
                    property.name.equals(other = fieldName, ignoreCase = true)
        }.mapNotNull { property ->
            runCatching {
                // Attempt to retrieve the Column property from the table.
                tracer.debug("Column matched. ${table.tableName}::${property.name}.")
                return@runCatching property.getter.call(table) as? Column<*>
            }.onFailure { exception ->
                // Log the exception if the reflection call fails, as it may indicate a misconfiguration.
                tracer.error(message = "Failed to access column. ${table.tableName}::${property.name}", cause = exception)
            }.getOrNull()
        }
    }

    /**
     * Generates a unique cache key for column lookup by combining all table names involved in the query
     * with the specified sorting directives.
     *
     * If the sorting table name is not specified in the directives, it defaults to `null`, maintaining uniqueness.
     * This design allows caching the same column under two keys to optimize column retrieval,
     * as long as no ambiguity is detected (e.g., `query-tables=null.fieldName` or `query-tables=tableName.fieldName`).
     *
     * This approach prevents skipping essential ambiguity checks for fields that appear across different
     * tables in different queries.
     *
     * @param queryTables All the tables involved in the query.
     * @param sort The sorting directive used to refine the key.
     * @return A string representing the cache key, uniquely identifying a column within the query context.
     */
    private fun generateCacheKey(queryTables: List<Table>, sort: Pageable.Sort): String {
        val tableNames: String = queryTables.joinToString("::") { it.tableName.lowercase() }
        return "$tableNames=${sort.table?.lowercase()}.${sort.field.lowercase()}"
    }
}
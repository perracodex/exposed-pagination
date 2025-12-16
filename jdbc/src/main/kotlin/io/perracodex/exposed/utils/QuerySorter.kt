/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

package io.perracodex.exposed.utils

import io.perracodex.exposed.pagination.Pageable
import io.perracodex.exposed.pagination.PaginationError
import io.perracodex.exposed.utils.QuerySorter.columnCache
import io.perracodex.exposed.utils.QuerySorter.generateCacheKey
import io.perracodex.exposed.utils.QuerySorter.generateExpressionCacheKey
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.IExpressionAlias
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Query
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.full.memberProperties

/**
 * Responsible for determining and applying column-based ordering to database [Query] instances.
 *
 * It handles the resolution of columns across multiple tables, manages caching for performance optimization,
 * and ensures that sorting directives are applied unambiguously.
 *
 * **Key Responsibilities:**
 * - **Sorting Application:** Applies multiple sorting directives to a [Query].
 * - **Column Resolution:** Resolves column references from sorting directives, handling ambiguities.
 * - **Caching:** Utilizes a cache to manage column references, minimizing the overhead of reflection-based resolution.
 *
 * @see [Pageable.PageSort]
 * @see [Query]
 * @see [PaginationError]
 */
internal object QuerySorter {
    private val tracer = Tracer<QuerySorter>()

    /**
     * A cache that stores resolved column references to optimize column lookup operations.
     *
     * This cache maps a unique key, generated based on query tables and sorting directives,
     * to their corresponding [Column] instances. By caching these references, the [QuerySorter]
     * minimizes the performance cost associated with reflection-based column resolution.
     *
     * @see [generateCacheKey]
     */
    private val columnCache: MutableMap<String, Column<*>> = ConcurrentHashMap()

    /**
     * A cache that stores resolved expression references to optimize expression alias lookup operations.
     *
     * This cache maps a unique key, generated based on query fields and sorting directives,
     * to their corresponding [Expression] instances. Used primarily for union queries where
     * fields are represented as [IExpressionAlias] rather than table columns.
     *
     * @see [generateExpressionCacheKey]
     */
    private val expressionCache: MutableMap<String, Expression<*>> = ConcurrentHashMap()

    /**
     * Applies the specified sorting directives to the given [Query], by processing
     * each [Pageable.PageSort] directive, resolves the corresponding [Column] within
     * the context of the query's tables, and applies the sorting order to the [Query].
     *
     * @param query The [Query] instance to which the sorting directives will be applied.
     * @param sortDirectives A list of [Pageable.PageSort] directives defining the sorting order.
     * @throws PaginationError.InvalidSortDirective If a sort directive references a non-existent field or table.
     * @throws PaginationError.AmbiguousSortField If a sort directive's field is found in multiple tables
     * without explicit table specification.
     *
     * @see [Pageable.PageSort]
     * @see [PaginationError]
     */
    fun applyOrder(query: Query, sortDirectives: List<Pageable.PageSort>) {
        if (sortDirectives.isEmpty()) {
            return
        }

        // Collect all expression fields, including from QueryAlias sources.
        val expressionFields: List<Expression<*>> = collectExpressionFields(query = query)
        val queryTables: List<Table> = query.targets.distinct()

        sortDirectives.forEach { sort ->
            // Determine the sort order based on the directive.
            val sortOrder: SortOrder = when (sort.direction) {
                Pageable.PageDirection.ASC -> SortOrder.ASC
                Pageable.PageDirection.DESC -> SortOrder.DESC
            }

            // First, try to resolve the field from expression aliases (e.g., union queries).
            val expressionKey: String = generateExpressionCacheKey(queryFields = expressionFields, sort = sort)
            val expressionAlias: Expression<*>? = getExpressionAlias(
                key = expressionKey,
                sort = sort,
                fields = expressionFields
            )

            if (expressionAlias != null) {
                // Apply sorting using the expression alias.
                query.orderBy(expressionAlias to sortOrder)
            } else {
                // Fall back to table column resolution.
                val targetTables: List<Table> = findTargetTables(queryTables = queryTables, sort = sort)
                val columnKey: String = generateCacheKey(queryTables = queryTables, sort = sort)
                val column: Column<*> = getColumn(
                    key = columnKey,
                    sort = sort,
                    targets = targetTables
                )
                query.orderBy(column to sortOrder)
            }
        }
    }

    /**
     * Collects all expression fields from the query, including fields from nested [QueryAlias] sources.
     *
     * For union queries or subqueries wrapped in [QueryAlias], the original expression aliases
     * are preserved in the inner query's field set. This method traverses the query structure
     * to find and return those original [IExpressionAlias] fields that can be used for sorting.
     *
     * @param query The [Query] instance to collect expression fields from.
     * @return A list of [Expression] instances, including any [IExpressionAlias] from nested queries.
     */
    private fun collectExpressionFields(query: Query): List<Expression<*>> {
        val fields: MutableList<Expression<*>> = query.set.fields.toMutableList()

        // Check if the query source is a QueryAlias (e.g., from union queries).
        val source = query.set.source
        if (source is QueryAlias) {
            // Add fields from the inner query which may contain IExpressionAlias.
            fields.addAll(source.query.set.fields)
        }

        return fields.toList()
    }

    /**
     * Identifies the target tables within the [Query] that correspond to a given [sort] directive.
     *
     * This method filters the list of tables involved in the query based on the table name
     * specified in the [Pageable.PageSort] directive. If a table name is provided, it selects
     * only the table that matches the specified name. If no table name is specified,
     * it returns all tables involved in the [Query].
     *
     * @param queryTables The list of all the [Table] instances involved in the current [Query].
     * @param sort The [Pageable.PageSort] directive containing the table name (if any) and field name.
     *
     * @return A list of [Table] objects that match the sorting directive's table name, or all tables if none is specified.
     * @throws PaginationError.InvalidSortDirective If the specified table name does not exist within the query's tables.
     *
     * @see [Pageable.PageSort]
     * @see [PaginationError]
     */
    private fun findTargetTables(queryTables: List<Table>, sort: Pageable.PageSort): List<Table> {
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
     * Attempts to retrieve the [Column] associated with a sorting directive, utilizing caching for efficiency.
     *
     * This method first attempts to fetch the [Column] from the [columnCache] using the provided key.
     * If the column is not cached, it resolves the column through reflection by searching the target
     * tables for a matching field name. Upon successful resolution, the column is cached for future
     * retrievals.
     *
     * @param key A unique string key generated from the query tables and sort directive.
     * @param sort The [Pageable.PageSort] directive containing the field name and sort direction.
     * @param targets A list of [Table] instances to search for the corresponding column.
     * @return The resolved [Column] instance corresponding to the sort directive.
     * @throws PaginationError.InvalidSortDirective If the field specified in the sort directive does not exist in the target tables.
     * @throws PaginationError.AmbiguousSortField If the field exists in multiple tables without explicit table specification.
     *
     * @see [Pageable.PageSort]
     * @see [PaginationError]
     */
    private fun getColumn(key: String, sort: Pageable.PageSort, targets: List<Table>): Column<*> {
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
     * Resolves a [Column] within a specific [Table] based on the provided field name,
     * by using reflection to search for a property within the [Table] class that matches
     * the specified [fieldName]. It filters properties to identify those that are of type [Column]
     * and have a name matching the field name (case-insensitive).
     *
     * Using the Table class properties to resolve the columns instead of the actual table field name
     * at database level, allows to abstract clients from the actual database field name definition,
     * which could be different from the property name in the Table class.
     *
     * @param table The [Table] in which to search for the column.
     * @param fieldName The name of the field representing the column to be resolved.
     * @return A list of [Column] instances that match the specified field name within the table.
     *
     * **Note:** Typically, this list should contain either zero or one [Column]. Multiple matches indicate
     * a configuration issue where multiple columns share the same field name within a table.
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
     * Generates a unique cache key based on the [Query] tables and a [Pageable.PageSort] directive.
     *
     * The cache key is constructed by concatenating the names of all tables involved in the [Query],
     * followed by the table name (if specified) the field belongs to, and finally the field name.
     * This unique key ensures that cached columns are associated only with their respective query
     * contexts and sorting criteria.
     *
     * **Example Key Format:**
     * - With table specified: `"employees::departments::contracts=employees.firstName"`
     * - Without table specified: `"employees::departments::contracts=firstName"`
     *
     * @param queryTables The list of [Table] objects involved in the [Query].
     * @param sort The [Pageable.PageSort] directive containing the table name (optional) and field name.
     * @return A unique string key representing the combination of query tables and sorting directive.
     */
    private fun generateCacheKey(queryTables: List<Table>, sort: Pageable.PageSort): String {
        val tableNames: String = queryTables.joinToString("::") { it.tableName.lowercase() }
        return "$tableNames=${sort.table?.lowercase()}.${sort.field.lowercase()}"
    }

    /**
     * Attempts to retrieve an [Expression] from expression aliases within query fields.
     *
     * This method is used primarily for union queries or queries with computed expressions
     * where fields are represented as [IExpressionAlias] rather than direct table columns.
     *
     * @param key A unique string key for caching the resolved expression.
     * @param sort The [Pageable.PageSort] directive containing the field name.
     * @param fields The list of [Expression] instances from the query's field set.
     * @return The resolved [Expression] if found, or `null` if no matching alias exists.
     */
    private fun getExpressionAlias(
        key: String,
        sort: Pageable.PageSort,
        fields: List<Expression<*>>
    ): Expression<*>? {
        // Check if the expression is already cached.
        expressionCache[key]?.let { expression ->
            return expression
        }

        // Search for a matching expression alias in the query fields.
        val matchingAliases: List<IExpressionAlias<*>> = fields
            .filterIsInstance<IExpressionAlias<*>>()
            .filter { expressionAlias ->
                expressionAlias.alias.equals(other = sort.field, ignoreCase = true)
            }

        if (matchingAliases.isEmpty()) {
            return null
        }

        if (matchingAliases.size > 1) {
            val reason = "'${sort.field}' found multiple times as expression alias"
            throw PaginationError.AmbiguousSortField(sort = sort, reason = reason)
        }

        // Cache and return the alias-only expression for sorting.
        val expression: Expression<*> = matchingAliases.single().aliasOnlyExpression()
        expressionCache[key] = expression
        tracer.debug("Expression alias matched: ${sort.field}")
        return expression
    }

    /**
     * Generates a unique cache key for expression alias resolution.
     *
     * The key is constructed from the hash codes of query fields combined with the sort field name,
     * ensuring uniqueness across different query contexts.
     *
     * @param queryFields The list of [Expression] instances from the query's field set.
     * @param sort The [Pageable.PageSort] directive containing the field name.
     * @return A unique string key for expression caching.
     */
    private fun generateExpressionCacheKey(queryFields: List<Expression<*>>, sort: Pageable.PageSort): String {
        val fieldsHash: Int = queryFields.map { it.hashCode() }.hashCode()
        return "expr:$fieldsHash=${sort.field.lowercase()}"
    }
}

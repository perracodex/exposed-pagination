/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

package io.perracodex.exposed.utils

import io.perracodex.exposed.pagination.Pageable
import io.perracodex.exposed.pagination.PaginationError
import io.perracodex.exposed.utils.SortParameterParser.FIELD_SEGMENT_DELIMITER
import io.perracodex.exposed.utils.SortParameterParser.SORT_SEGMENT_DELIMITER

/**
 * Provides utility functions to parse sorting parameters
 * into a list of [Pageable.PageSort] directives.
 */
internal object SortParameterParser {
    /** Delimiter used to split the sort parameter into field name and direction (e.g., "fieldName,ASC"). */
    private const val SORT_SEGMENT_DELIMITER: Char = ','

    /** Delimiter used to split a field segment into table name and field name (e.g., "table.fieldName"). */
    private const val FIELD_SEGMENT_DELIMITER: Char = '.'

    /**
     * Index position for the field name in a split sort segment
     * (assuming the segment is split by [SORT_SEGMENT_DELIMITER]).
     * For example, in the segment "fieldName,ASC", the field name is at index 0.
     */
    private const val FIELD_SEGMENT_INDEX: Int = 0

    /**
     * Index position for the direction (ASC or DESC) in a split sort segment.
     * For example, in the segment "fieldName,ASC", the direction is at index 1
     * corresponding to the value "ASC".
     */
    private const val DIRECTION_SEGMENT_INDEX: Int = 1

    /**
     * Index position for the table name in a split field segment
     * (assuming the segment is split by [FIELD_SEGMENT_DELIMITER]).
     * For example, in the segment "table.fieldName", the table name is at index 0.
     */
    private const val TABLE_NAME_INDEX: Int = 0

    /**
     * Index position for the field name in a split field segment.
     * For example, in the segment "table.fieldName", the field name is at index 1.
     */
    private const val FIELD_NAME_INDEX: Int = 1

    /**
     * Parses the sorting parameters into a list of [Pageable.PageSort] directives.
     *
     * @param sortParameters The list of sorting parameters to parse.
     * @return A list of [Pageable.PageSort] directives representing the sorting configuration.
     */
    fun getSortDirectives(sortParameters: List<String>): List<Pageable.PageSort>? {
        return sortParameters.mapNotNull { parameter ->
            if (parameter.isBlank()) {
                throw PaginationError.MissingSortDirective()
            }

            val sortSegments: List<String> = parameter.split(SORT_SEGMENT_DELIMITER).map(String::trim)

            return@mapNotNull if (sortSegments.isEmpty()) {
                null
            } else {
                // Resolve the table and field names from the field segment.
                val fieldSegment: String = sortSegments[FIELD_SEGMENT_INDEX]
                val tableColumnPair: TableColumnPair = parseTableAndField(segment = fieldSegment)

                // Resolve the sorting direction from the segment.
                val direction: Pageable.PageDirection = if (sortSegments.size >= 2) {
                    runCatching {
                        Pageable.PageDirection.valueOf(sortSegments[DIRECTION_SEGMENT_INDEX].uppercase())
                    }.getOrElse {
                        throw PaginationError.InvalidOrderDirection(direction = sortSegments[DIRECTION_SEGMENT_INDEX])
                    }
                } else {
                    // If no direction is specified, default to ascending.
                    Pageable.PageDirection.ASC
                }

                Pageable.PageSort(
                    table = tableColumnPair.table,
                    field = tableColumnPair.field,
                    direction = direction
                )
            }
        }.takeIf { sortDirectives ->
            sortDirectives.isNotEmpty()
        }
    }

    /**
     * Parses the table name and field name from a segment of a sort parameter.
     *
     * @param segment The segment of a sort parameter to parse the table and field names from.
     * @return A [TableColumnPair] object containing the table and field names.
     */
    private fun parseTableAndField(segment: String): TableColumnPair {
        return if (segment.contains(FIELD_SEGMENT_DELIMITER)) {
            val fieldParts: List<String> = segment.split(FIELD_SEGMENT_DELIMITER)
            TableColumnPair(table = fieldParts[TABLE_NAME_INDEX], field = fieldParts[FIELD_NAME_INDEX])
        } else {
            // No table specified.
            TableColumnPair(table = null, field = segment)
        }
    }

    /**
     * Represents a table and column name pair.
     *
     * @property table Optional name of the table the field belongs to. Used to avoid ambiguity.
     * @property field The name of the field to sort by.
     */
    private data class TableColumnPair(val table: String?, val field: String)
}

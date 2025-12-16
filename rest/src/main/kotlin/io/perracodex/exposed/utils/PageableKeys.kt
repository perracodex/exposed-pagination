/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

package io.perracodex.exposed.utils

/**
 * Defines the query parameter keys used to extract pagination
 * and sorting directives from an HTTP request.
 */
internal enum class PageableKeys(val key: String) {
    /** The query parameter key for the page index. */
    PAGE("page"),

    /** The query parameter key for the absolute starting position (offset). */
    POSITION("position"),

    /** The query parameter key for the page size. */
    SIZE("size"),

    /** The query parameter key for sorting directives. */
    SORT("sort")
}

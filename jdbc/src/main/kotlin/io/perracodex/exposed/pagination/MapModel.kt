/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

package io.perracodex.exposed.pagination

import org.jetbrains.exposed.v1.core.ResultRow

/**
 * Contract for mapping a database [ResultRow] into an instance of type [T].
 *
 * **Usage:**
 * The `MapModel` interface is used to define how a database row or group of rows is transformed into
 * a domain model. This is particularly useful when working with pagination, especially for queries
 * involving relationships (e.g., 1 -> N or N -> N).
 *
 * #### Simple Mapping:
 * ```
 * fun findAll(pageable: Pageable?): Page<Employee> {
 *     return transaction {
 *         EmployeeTable.selectAll().paginate(
 *             pageable = pageable,
 *             map = object : MapModel<Employee> {
 *                 override fun from(row: ResultRow): Employee {
 *                     return Employee(
 *                         id = row[EmployeeTable.id],
 *                         firstName = row[EmployeeTable.firstName],
 *                         lastName = row[EmployeeTable.lastName]
 *                     )
 *                 }
 *             }
 *         )
 *     }
 * }
 * ```
 *
 * Alternatively, the domain model can implement `MapModel` via a companion object:
 * ```
 * fun findAll(pageable: Pageable?): Page<Employee> {
 *     return transaction {
 *         EmployeeTable
 *         .selectAll()
 *         .paginate(pageable = pageable, map = Employee)
 *     }
 * }
 *
 * data class Employee(
 *     val id: Int,
 *     val firstName: String,
 *     val lastName: String
 * ) {
 *     companion object : MapModel<Employee> {
 *         override fun from(row: ResultRow): Employee {
 *             return Employee(
 *                 id = row[EmployeeTable.id],
 *                 firstName = row[EmployeeTable.firstName],
 *                 lastName = row[EmployeeTable.lastName]
 *             )
 *         }
 *     }
 * }
 * ```
 *
 * #### Handling Relationships
 * When working with 1 -> N relationships, use the `groupBy` parameter to group rows by the primary key
 * of the main entity. This ensures that pagination operates on the correct count of unique top-level entities,
 * while still allowing related entities to be aggregated during the mapping process.
 *
 * ```
 * fun findAll(pageable: Pageable?): Page<Employee> {
 *     return transaction {
 *         EmployeeTable
 *             .selectAll()
 *             .paginate(
 *                 pageable = pageable,
 *                 map = Employee,
 *                 groupBy = EmployeeTable.id
 *             )
 *     }
 * }
 *
 * data class Employee(
 *     val id: Uuid,
 *     val firstName: String,
 *     val lastName: String,
 *     val contact: List<Contact>,
 *     val employments: List<Employment>
 * ) {
 *     companion object : MapModel<Employee> {
 *         override fun from(row: ResultRow): Employee {
 *             val firstName: String = row[EmployeeTable.firstName]
 *             val lastName: String = row[EmployeeTable.lastName]
 *             return Employee(
 *                 id = row[EmployeeTable.id],
 *                 firstName = firstName,
 *                 lastName = lastName,
 *                 contact = listOf(),
 *                 employments = listOf()
 *             )
 *         }
 *
 *         override fun from(rows: List<ResultRow>): Employee? {
 *             if (rows.isEmpty()) {
 *                 return null
 *             }
 *
 *             // As we are handling a 1 -> N relationship,
 *             // we only need the first row to extract the top-level record.
 *             val topLevelRecord: ResultRow = rows.first()
 *             val employee: Employee = from(row = topLevelRecord)
 *
 *             // Extract Contacts. Each must perform its own mapping.
 *             val contact: List<Contact> = rows.mapNotNull { row ->
 *                 row.getOrNull(ContactTable.id)?.let {
 *                     // Contact must perform its own mapping.
 *                     Contact.from(row = row)
 *                 }
 *             }
 *
 *             // Extract Employments. Each must perform its own mapping.
 *             val employments: List<Employment> = rows.mapNotNull { row ->
 *                 row.getOrNull(EmploymentTable.id)?.let {
 *                     // Employment must perform its own mapping.
 *                     Employment.from(row = row)
 *                 }
 *             }
 *
 *             return employee.copy(
 *                 contact = contact,
 *                 employments = employments
 *             )
 *         }
 *     }
 * }
 * ```
 *
 * **Notes on `groupBy`:**
 * - The `groupBy` parameter should refer to the primary key of the main entity.
 * - It is used to compute the accurate number of top-level entities and facilitate paginated queries
 *   while supporting aggregation of related data.
 *
 * @param T The type of the domain model to map into.
 */
public interface MapModel<T> {
    /**
     * Maps a database [ResultRow] into a domain model of type [T].
     *
     * Implement this method to define how database results are mapped into domain models.
     *
     * @param row The [ResultRow] retrieved from a database query.
     * @return An instance of [T] representing the mapped domain model.
     */
    public fun from(row: ResultRow): T

    /**
     * Maps a group of [ResultRow] instances into a single domain model of type [T],
     * aggregating related entities as needed.
     *
     * It is expected that the mapping logic will handle the aggregation of related entities
     * converting the list of [ResultRow] instances into a single domain model.
     *
     * Default implementation maps only the first row.
     * Models that require aggregation should override this method.
     *
     * @param rows The list of [ResultRow] instances retrieved from a database query.
     * @return An instance of [T] representing the mapped domain model, or `null` if the list is empty.
     */
    public fun from(rows: List<ResultRow>): T? {
        if (rows.isEmpty()) {
            return null
        }
        return from(row = rows.first())
    }
}

/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

package io.perracodex.exposed

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.perracodex.exposed.pagination.MapModel
import io.perracodex.exposed.pagination.Page
import io.perracodex.exposed.pagination.Pageable
import io.perracodex.exposed.pagination.paginate
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.ExpressionWithColumnTypeAlias
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.union

/**
 * Integration tests for sorting on UNION queries.
 * Tests that sorting can be applied correctly to the result of combining multiple tables.
 */
class UnionQuerySortingTest : FunSpec({

    // Table for employees.
    val employeesTable = object : Table("employees") {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 100)
        val salary = integer("salary")
        val departmentId = integer("department_id")
        override val primaryKey = PrimaryKey(id)
    }

    // Table for contractors.
    val contractorsTable = object : Table("contractors") {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 100)
        val hourlyRate = integer("hourly_rate")
        val projectId = integer("project_id")
        override val primaryKey = PrimaryKey(id)
    }

    // Unified model for workers (employees and contractors).
    data class Worker(
        val id: Int,
        val name: String,
        val rate: Int,
        val assignmentId: Int,
        val type: String
    )

    // Column aliases for union query, initialized per transaction.
    data class ColumnAliases(
        val id: ExpressionWithColumnTypeAlias<Int>,
        val name: ExpressionWithColumnTypeAlias<String>,
        val rate: ExpressionWithColumnTypeAlias<Int>,
        val assignmentId: ExpressionWithColumnTypeAlias<Int>,
        val type: ExpressionWithColumnTypeAlias<String>
    )

    // Resolved column references from QueryAlias - must be created once and reused.
    data class UnionColumns(
        val id: ExpressionWithColumnType<Int>,
        val name: ExpressionWithColumnType<String>,
        val rate: ExpressionWithColumnType<Int>,
        val assignmentId: ExpressionWithColumnType<Int>,
        val type: ExpressionWithColumnType<String>
    )

    // Result of building a union query with all necessary components for mapping.
    data class UnionQueryResult(
        val query: Query,
        val unionAlias: QueryAlias,
        val columnAliases: ColumnAliases,
        val columns: UnionColumns
    )

    lateinit var database: Database

    /**
     * Creates column aliases for union query.
     * Should be called within each transaction.
     */
    fun createColumnAliases(): ColumnAliases {
        return ColumnAliases(
            id = employeesTable.id.alias("id"),
            name = employeesTable.name.alias("name"),
            rate = employeesTable.salary.alias("rate"),
            assignmentId = employeesTable.departmentId.alias("assignmentId"),
            type = stringLiteral("EMPLOYEE").alias("type")
        )
    }

    /**
     * Builds a union query combining employees and contractors.
     * Returns the query, union alias, and column aliases for use in the mapper.
     */
    fun buildWorkersUnionQuery(): UnionQueryResult {
        val aliases = createColumnAliases()

        // Columns for employees query.
        val employeeColumns = listOf(
            aliases.id,
            aliases.name,
            aliases.rate,
            aliases.assignmentId,
            aliases.type,
        )

        // Columns for contractors query.
        val contractorColumns = listOf(
            contractorsTable.id,
            contractorsTable.name,
            contractorsTable.hourlyRate,
            contractorsTable.projectId,
            stringLiteral("CONTRACTOR")
        )

        // Build union query.
        val employeeQuery = employeesTable.select(employeeColumns)
        val contractorQuery = contractorsTable.select(contractorColumns)

        val unionQuery = employeeQuery.union(contractorQuery)
        val unionAlias = unionQuery.alias("workers")

        // Create column references ONCE and reuse them - unionAlias[x] creates new object each time.
        val columns = UnionColumns(
            id = unionAlias[aliases.id],
            name = unionAlias[aliases.name],
            rate = unionAlias[aliases.rate],
            assignmentId = unionAlias[aliases.assignmentId],
            type = unionAlias[aliases.type]
        )

        // Use select() with exact same column references that will be used in mapper.
        val query = unionAlias.select(columns.id, columns.name, columns.rate, columns.assignmentId, columns.type)

        return UnionQueryResult(
            query = query,
            unionAlias = unionAlias,
            columnAliases = aliases,
            columns = columns
        )
    }

    /**
     * Creates a mapper for workers from union query result.
     * Uses pre-resolved column references from UnionColumns.
     */
    fun createWorkerMapper(columns: UnionColumns): MapModel<Worker> {
        return object : MapModel<Worker> {
            override fun from(row: ResultRow): Worker {
                return Worker(
                    id = row[columns.id],
                    name = row[columns.name],
                    rate = row[columns.rate],
                    assignmentId = row[columns.assignmentId],
                    type = row[columns.type]
                )
            }
        }
    }

    beforeSpec {
        database = Database.connect(
            url = "jdbc:h2:mem:union_sorting_test_db;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
        transaction(database) {
            SchemaUtils.create(employeesTable, contractorsTable)

            // Insert employees with different salaries for sorting tests.
            employeesTable.insert {
                it[name] = "Alice"
                it[salary] = 5000
                it[departmentId] = 1
            }
            employeesTable.insert {
                it[name] = "Bob"
                it[salary] = 7000
                it[departmentId] = 2
            }
            employeesTable.insert {
                it[name] = "Charlie"
                it[salary] = 4000
                it[departmentId] = 1
            }

            // Insert contractors with different hourly rates.
            contractorsTable.insert {
                it[name] = "Diana"
                it[hourlyRate] = 6000
                it[projectId] = 10
            }
            contractorsTable.insert {
                it[name] = "Edward"
                it[hourlyRate] = 3000
                it[projectId] = 11
            }
            contractorsTable.insert {
                it[name] = "Frank"
                it[hourlyRate] = 8000
                it[projectId] = 10
            }
        }
    }

    afterSpec {
        transaction(database) {
            SchemaUtils.drop(employeesTable, contractorsTable)
        }
    }

    test("union query without sorting should return all workers") {
        transaction(database) {
            val result = buildWorkersUnionQuery()
            val workerMapper = createWorkerMapper(result.columns)
            val page: Page<Worker> = result.query.paginate(null, workerMapper)

            page.content.size shouldBe 6
            page.details.totalElements shouldBe 6
        }
    }

    test("union query with pagination should return correct page size") {
        transaction(database) {
            val pageable = Pageable(page = 0, position = null, size = 3)
            val result = buildWorkersUnionQuery()
            val workerMapper = createWorkerMapper(result.columns)
            val page: Page<Worker> = result.query.paginate(pageable, workerMapper)

            page.content.size shouldBe 3
            page.details.totalElements shouldBe 6
            page.details.totalPages shouldBe 2
        }
    }

    test("union query with ASC sorting by rate should return workers sorted by rate ascending") {
        transaction(database) {
            val sortDirective = Pageable.PageSort(field = "rate", direction = Pageable.PageDirection.ASC)
            val pageable = Pageable(page = 0, position = null, size = 10, sort = listOf(sortDirective))

            val result = buildWorkersUnionQuery()
            val workerMapper = createWorkerMapper(result.columns)
            val page: Page<Worker> = result.query.paginate(pageable, workerMapper)

            page.content.size shouldBe 6
            // Verify ascending order by rate: 3000, 4000, 5000, 6000, 7000, 8000.
            page.content[0].rate shouldBe 3000
            page.content[0].name shouldBe "Edward"
            page.content[1].rate shouldBe 4000
            page.content[1].name shouldBe "Charlie"
            page.content[2].rate shouldBe 5000
            page.content[2].name shouldBe "Alice"
            page.content[3].rate shouldBe 6000
            page.content[3].name shouldBe "Diana"
            page.content[4].rate shouldBe 7000
            page.content[4].name shouldBe "Bob"
            page.content[5].rate shouldBe 8000
            page.content[5].name shouldBe "Frank"
        }
    }

    test("union query with DESC sorting by rate should return workers sorted by rate descending") {
        transaction(database) {
            val sortDirective = Pageable.PageSort(field = "rate", direction = Pageable.PageDirection.DESC)
            val pageable = Pageable(page = 0, position = null, size = 10, sort = listOf(sortDirective))

            val result = buildWorkersUnionQuery()
            val workerMapper = createWorkerMapper(result.columns)
            val page: Page<Worker> = result.query.paginate(pageable, workerMapper)

            page.content.size shouldBe 6
            // Verify descending order by rate: 8000, 7000, 6000, 5000, 4000, 3000.
            page.content[0].rate shouldBe 8000
            page.content[0].name shouldBe "Frank"
            page.content[1].rate shouldBe 7000
            page.content[1].name shouldBe "Bob"
            page.content[2].rate shouldBe 6000
            page.content[2].name shouldBe "Diana"
            page.content[3].rate shouldBe 5000
            page.content[3].name shouldBe "Alice"
            page.content[4].rate shouldBe 4000
            page.content[4].name shouldBe "Charlie"
            page.content[5].rate shouldBe 3000
            page.content[5].name shouldBe "Edward"
        }
    }

    test("union query with sorting by name ASC should return workers sorted alphabetically") {
        transaction(database) {
            val sortDirective = Pageable.PageSort(field = "name", direction = Pageable.PageDirection.ASC)
            val pageable = Pageable(page = 0, position = null, size = 10, sort = listOf(sortDirective))

            val result = buildWorkersUnionQuery()
            val workerMapper = createWorkerMapper(result.columns)
            val page: Page<Worker> = result.query.paginate(pageable, workerMapper)

            page.content.size shouldBe 6
            // Verify alphabetical order: Alice, Bob, Charlie, Diana, Edward, Frank.
            page.content[0].name shouldBe "Alice"
            page.content[1].name shouldBe "Bob"
            page.content[2].name shouldBe "Charlie"
            page.content[3].name shouldBe "Diana"
            page.content[4].name shouldBe "Edward"
            page.content[5].name shouldBe "Frank"
        }
    }

    test("union query with sorting and pagination should return correct sorted page") {
        transaction(database) {
            val sortDirective = Pageable.PageSort(field = "rate", direction = Pageable.PageDirection.DESC)
            val pageable = Pageable(page = 0, position = null, size = 3, sort = listOf(sortDirective))

            val result = buildWorkersUnionQuery()
            val workerMapper = createWorkerMapper(result.columns)
            val page: Page<Worker> = result.query.paginate(pageable, workerMapper)

            page.content.size shouldBe 3
            page.details.totalPages shouldBe 2
            // First page should contain top 3 by rate: Frank(8000), Bob(7000), Diana(6000).
            page.content[0].name shouldBe "Frank"
            page.content[1].name shouldBe "Bob"
            page.content[2].name shouldBe "Diana"
        }
    }

    test("union query second page with sorting should return correct offset") {
        transaction(database) {
            val sortDirective = Pageable.PageSort(field = "rate", direction = Pageable.PageDirection.DESC)
            val pageable = Pageable(page = 1, position = null, size = 3, sort = listOf(sortDirective))

            val result = buildWorkersUnionQuery()
            val workerMapper = createWorkerMapper(result.columns)
            val page: Page<Worker> = result.query.paginate(pageable, workerMapper)

            page.content.size shouldBe 3
            page.details.pageIndex shouldBe 1
            // Second page should contain: Alice(5000), Charlie(4000), Edward(3000).
            page.content[0].name shouldBe "Alice"
            page.content[1].name shouldBe "Charlie"
            page.content[2].name shouldBe "Edward"
        }
    }

    test("union query with multiple sort directives should apply compound sorting") {
        transaction(database) {
            // Sort by type first (ASC), then by rate (DESC).
            val sortDirectives = listOf(
                Pageable.PageSort(field = "type", direction = Pageable.PageDirection.ASC),
                Pageable.PageSort(field = "rate", direction = Pageable.PageDirection.DESC)
            )
            val pageable = Pageable(page = 0, position = null, size = 10, sort = sortDirectives)

            val result = buildWorkersUnionQuery()
            val workerMapper = createWorkerMapper(result.columns)
            val page: Page<Worker> = result.query.paginate(pageable, workerMapper)

            page.content.size shouldBe 6
            // CONTRACTOR comes before EMPLOYEE alphabetically.
            // Contractors sorted by rate DESC: Frank(8000), Diana(6000), Edward(3000).
            // Employees sorted by rate DESC: Bob(7000), Alice(5000), Charlie(4000).
            page.content[0].type shouldBe "CONTRACTOR"
            page.content[0].name shouldBe "Frank"
            page.content[1].type shouldBe "CONTRACTOR"
            page.content[1].name shouldBe "Diana"
            page.content[2].type shouldBe "CONTRACTOR"
            page.content[2].name shouldBe "Edward"
            page.content[3].type shouldBe "EMPLOYEE"
            page.content[3].name shouldBe "Bob"
            page.content[4].type shouldBe "EMPLOYEE"
            page.content[4].name shouldBe "Alice"
            page.content[5].type shouldBe "EMPLOYEE"
            page.content[5].name shouldBe "Charlie"
        }
    }
})

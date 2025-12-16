/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

package io.perracodex.exposed.pagination

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Unit tests for pagination query functionality.
 */
class PaginationQueryTest : FunSpec({

    // Test table definition.
    val testTable = object : Table("test_items") {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 255)
        override val primaryKey = PrimaryKey(id)
    }

    // Test model.
    data class TestItem(val id: Int, val name: String)

    // Model mapper.
    val testItemMapper = object : MapModel<TestItem> {
        override fun from(row: ResultRow): TestItem {
            return TestItem(
                id = row[testTable.id],
                name = row[testTable.name]
            )
        }
    }

    lateinit var database: Database

    beforeSpec {
        database = Database.connect(
            url = "jdbc:h2:mem:pagination_query_test;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
        transaction(database) {
            SchemaUtils.drop(testTable)
            SchemaUtils.create(testTable)
            // Insert test data: 25 items.
            repeat(25) { index ->
                testTable.insert {
                    it[name] = "Item ${index + 1}"
                }
            }
        }
    }

    afterSpec {
        transaction(database) {
            SchemaUtils.drop(testTable)
        }
    }

    test("pagination should return correct page size") {
        transaction(database) {
            val pageable = Pageable(page = 0, position = null, size = 10)
            val page: Page<TestItem> = testTable.selectAll().paginate(pageable, testItemMapper)

            page.content.size shouldBe 10
            page.details.totalElements shouldBe 25
            page.details.totalPages shouldBe 3
            page.details.pageIndex shouldBe 0
            page.details.isFirst shouldBe true
            page.details.isLast shouldBe false
            page.details.hasNext shouldBe true
            page.details.hasPrevious shouldBe false
        }
    }

    test("pagination should return second page correctly") {
        transaction(database) {
            val pageable = Pageable(page = 1, position = null, size = 10)
            val page: Page<TestItem> = testTable.selectAll().paginate(pageable, testItemMapper)

            page.content.size shouldBe 10
            page.details.pageIndex shouldBe 1
            page.details.isFirst shouldBe false
            page.details.isLast shouldBe false
            page.details.hasNext shouldBe true
            page.details.hasPrevious shouldBe true
        }
    }

    test("pagination should return last page with remaining elements") {
        transaction(database) {
            val pageable = Pageable(page = 2, position = null, size = 10)
            val page: Page<TestItem> = testTable.selectAll().paginate(pageable, testItemMapper)

            page.content.size shouldBe 5
            page.details.pageIndex shouldBe 2
            page.details.isFirst shouldBe false
            page.details.isLast shouldBe true
            page.details.hasNext shouldBe false
            page.details.hasPrevious shouldBe true
        }
    }

    test("pagination with null pageable should return all elements") {
        transaction(database) {
            val page: Page<TestItem> = testTable.selectAll().paginate(null, testItemMapper)

            page.content.size shouldBe 25
            page.details.totalElements shouldBe 25
            page.details.totalPages shouldBe 1
        }
    }

    test("pagination should handle empty result set") {
        transaction(database) {
            val pageable = Pageable(page = 0, position = null, size = 10)
            val page: Page<TestItem> = testTable.selectAll()
                .where { testTable.id eq -1 }
                .paginate(pageable, testItemMapper)

            page.content.size shouldBe 0
            page.details.totalElements shouldBe 0
            page.details.totalPages shouldBe 0
        }
    }

    test("pagination with position should start from correct offset") {
        transaction(database) {
            val pageable = Pageable(page = 0, position = 5, size = 10)
            val page: Page<TestItem> = testTable.selectAll().paginate(pageable, testItemMapper)

            page.content.size shouldBe 10
            page.content.first().name shouldBe "Item 6"
            page.details.position shouldBe 5
        }
    }
})

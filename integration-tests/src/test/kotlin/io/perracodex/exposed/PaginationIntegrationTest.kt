/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

package io.perracodex.exposed

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.perracodex.exposed.pagination.MapModel
import io.perracodex.exposed.pagination.Page
import io.perracodex.exposed.pagination.Pageable
import io.perracodex.exposed.pagination.getPageable
import io.perracodex.exposed.pagination.paginate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Integration tests for pagination with Ktor server.
 * Tests the full flow: HTTP request -> PageableRequest parsing -> Database query -> Paginated response.
 */
class PaginationIntegrationTest : FunSpec({

    // Test table definition.
    val itemsTable = object : Table("items") {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 255)
        val price = integer("price")
        override val primaryKey = PrimaryKey(id)
    }

    // Serializable model for API responses.
    @Serializable
    data class Item(val id: Int, val name: String, val price: Int)

    // Model mapper for database rows.
    val itemMapper = object : MapModel<Item> {
        override fun from(row: ResultRow): Item {
            return Item(
                id = row[itemsTable.id],
                name = row[itemsTable.name],
                price = row[itemsTable.price]
            )
        }
    }

    lateinit var database: Database

    beforeSpec {
        database = Database.connect(
            url = "jdbc:h2:mem:integration_test_db;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
        transaction(database) {
            SchemaUtils.create(itemsTable)
            // Insert test data: 50 items with varying prices.
            repeat(50) { index ->
                itemsTable.insert {
                    it[name] = "Product ${index + 1}"
                    it[price] = (index + 1) * 10
                }
            }
        }
    }

    afterSpec {
        transaction(database) {
            SchemaUtils.drop(itemsTable)
        }
    }

    // Helper function to create test application with items endpoint.
    fun ApplicationTestBuilder.setupApplication() {
        application {
            install(ServerContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        ignoreUnknownKeys = true
                    }
                )
            }

            routing {
                get("/items") {
                    val pageable: Pageable? = call.getPageable()
                    val page: Page<Item> = transaction(database) {
                        itemsTable.selectAll().paginate(pageable, itemMapper)
                    }
                    call.respond(page)
                }
            }
        }
    }

    test("GET /items without pagination should return all items") {
        testApplication {
            setupApplication()

            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }

            val response = client.get("/items")

            response.status shouldBe HttpStatusCode.OK
            val page = response.body<Page<Item>>()
            page.content.size shouldBe 50
            page.details.totalElements shouldBe 50
            page.details.totalPages shouldBe 1
        }
    }

    test("GET /items with page and size should return paginated results") {
        testApplication {
            setupApplication()

            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }

            val response = client.get("/items?page=0&size=10")

            response.status shouldBe HttpStatusCode.OK
            val page = response.body<Page<Item>>()
            page.content.size shouldBe 10
            page.details.totalElements shouldBe 50
            page.details.totalPages shouldBe 5
            page.details.pageIndex shouldBe 0
            page.details.isFirst shouldBe true
            page.details.isLast shouldBe false
            page.details.hasNext shouldBe true
            page.details.hasPrevious shouldBe false
        }
    }

    test("GET /items second page should return correct offset") {
        testApplication {
            setupApplication()

            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }

            val response = client.get("/items?page=1&size=10")

            response.status shouldBe HttpStatusCode.OK
            val page = response.body<Page<Item>>()
            page.content.size shouldBe 10
            page.details.pageIndex shouldBe 1
            page.details.isFirst shouldBe false
            page.details.hasPrevious shouldBe true
            page.content.first().name shouldBe "Product 11"
        }
    }

    test("GET /items last page should have remaining elements") {
        testApplication {
            setupApplication()

            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }

            val response = client.get("/items?page=4&size=10")

            response.status shouldBe HttpStatusCode.OK
            val page = response.body<Page<Item>>()
            page.content.size shouldBe 10
            page.details.pageIndex shouldBe 4
            page.details.isLast shouldBe true
            page.details.hasNext shouldBe false
            page.content.first().name shouldBe "Product 41"
        }
    }

    test("GET /items with position should start from absolute offset") {
        testApplication {
            setupApplication()

            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }

            val response = client.get("/items?position=25&size=10")

            response.status shouldBe HttpStatusCode.OK
            val page = response.body<Page<Item>>()
            page.content.size shouldBe 10
            page.details.position shouldBe 25
            page.content.first().name shouldBe "Product 26"
        }
    }

    test("GET /items with sort should return sorted results") {
        testApplication {
            setupApplication()

            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }

            val response = client.get("/items?page=0&size=5&sort=price,desc")

            response.status shouldBe HttpStatusCode.OK
            val page = response.body<Page<Item>>()
            page.content.size shouldBe 5
            page.content.first().name shouldBe "Product 50"
            page.content.first().price shouldBe 500
            page.details.sort?.first()?.field shouldBe "price"
            page.details.sort?.first()?.direction shouldBe Pageable.PageDirection.DESC
        }
    }

    test("GET /items with multiple sort parameters should apply multi-field sorting") {
        testApplication {
            setupApplication()

            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }

            val response = client.get("/items?page=0&size=10&sort=price,asc&sort=name,desc")

            response.status shouldBe HttpStatusCode.OK
            val page = response.body<Page<Item>>()
            page.details.sort?.size shouldBe 2
            page.details.sort?.get(0)?.field shouldBe "price"
            page.details.sort?.get(0)?.direction shouldBe Pageable.PageDirection.ASC
            page.details.sort?.get(1)?.field shouldBe "name"
            page.details.sort?.get(1)?.direction shouldBe Pageable.PageDirection.DESC
        }
    }

    test("GET /items overflow page should indicate overflow") {
        testApplication {
            setupApplication()

            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }

            val response = client.get("/items?page=100&size=10")

            response.status shouldBe HttpStatusCode.OK
            val page = response.body<Page<Item>>()
            page.content.size shouldBe 0
            page.details.isOverflow shouldBe true
        }
    }
})

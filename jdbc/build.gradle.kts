/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

plugins {
    id("kotlin-library")
    id("vanniktech-publish")
}

group = project.properties["group"] as String
version = project.properties["version"] as String

// Module-specific properties for Maven publishing.
ext {
    set("pomName", "Exposed Pagination JDBC")
    set("pomDescription", "JDBC integration for Exposed ORM pagination, providing query pagination support.")
}

dependencies {
    api(project(":core"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.ktor.server.core)
    detektPlugins(libs.detekt.formatting)

    testImplementation(libs.h2)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

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
    set("pomName", "Exposed Pagination REST")
    set("pomDescription", "Ktor integration for Exposed ORM pagination, providing request parameter parsing.")
}

dependencies {
    api(project(":core"))
    implementation(libs.ktor.server.core)
    detektPlugins(libs.detekt.formatting)
    testImplementation(kotlin("test"))
}

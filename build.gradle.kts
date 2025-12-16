/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.vanniktech) apply false
    alias(libs.plugins.detekt) apply false
}

group = project.properties["group"] as String
version = project.properties["version"] as String

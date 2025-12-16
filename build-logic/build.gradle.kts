/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

plugins {
    `version-catalog`
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    compileOnly(gradleApi())
    compileOnly(gradleKotlinDsl())

    implementation(libs.gradle.kotlin.jvm)
    implementation(libs.gradle.kotlin.serialization)
    implementation(libs.gradle.dokka)
    implementation(libs.gradle.maven.publish)
    implementation(libs.gradle.detekt)
}

// Configure Kotlin compiler for better IDE support
kotlin {
    jvmToolchain(17)
}

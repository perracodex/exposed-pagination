/*
 * Copyright (c) 2024-Present Perracodex. Use of this source code is governed by an MIT license.
 */

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.gitlab.arturbosch.detekt")
}

// Configure Detekt for static code analysis.
detekt {
    buildUponDefaultConfig = true
    allRules = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
}

// Configure Detekt task reports.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(false)
        sarif.required.set(true)
    }
}

kotlin {
    jvmToolchain(jdkVersion = 17)

    // Enable explicit API mode.
    // https://github.com/Kotlin/KEEP/blob/master/proposals/explicit-api-mode.md
    // https://kotlinlang.org/docs/whatsnew14.html#explicit-api-mode-for-library-authors
    explicitApi()

    compilerOptions {
        extraWarnings.set(true)
    }
}

tasks.test {
    useJUnitPlatform()
}

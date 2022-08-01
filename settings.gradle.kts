pluginManagement {
    val kotlinVersion: String by settings
    val gradleNexusPublishVersion: String by settings

    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.spring") version kotlinVersion
        id("org.jetbrains.dokka") version kotlinVersion
        id("io.github.gradle-nexus.publish-plugin") version gradleNexusPublishVersion
    }
}

include(":graphglue-core")
include(":graphglue")
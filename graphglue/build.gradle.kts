description = "A library to develop annotation-based code-first GraphQL servers using GraphQL Kotlin, Spring Boot and Neo4j"

val springBootVersion: String by project
val graphqlKotlinVersion: String by project

plugins {
    kotlin("plugin.spring")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(path = ":graphglue-core"))
    api("org.springframework.boot", "spring-boot-starter-webflux", springBootVersion)
    api("com.expediagroup", "graphql-kotlin-spring-server", graphqlKotlinVersion)
}
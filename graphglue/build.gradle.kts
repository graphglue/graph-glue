description = "A library to develop annotation-based code-first GraphQL servers using GraphQL Kotlin, Spring Boot and Neo4j"

val graphqlKotlinVersion: String by project

plugins {
    kotlin("plugin.spring")
}

dependencies {
    api(project(path = ":graphglue-core"))
    api("com.expediagroup", "graphql-kotlin-spring-server", graphqlKotlinVersion)
}
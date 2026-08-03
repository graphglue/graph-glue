description =
    "A library to develop annotation-based code-first GraphQL servers using GraphQL Kotlin, Spring Boot and Neo4j"

plugins {
    id("graphglue.library-conventions")
}

dependencies {
    api(project(":graphglue-core"))
    api(libs.springBoot.starter.webflux)
    api(libs.graphqlKotlin.spring.server)
}

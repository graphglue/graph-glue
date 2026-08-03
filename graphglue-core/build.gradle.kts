description =
    "A library to develop annotation-based code-first GraphQL servers using GraphQL Kotlin, Spring Boot and Neo4j - excluding Spring GraphQL server dependencies"

plugins {
    id("graphglue.library-conventions")
}

dependencies {
    api(libs.springBoot.starter.json)
    api(libs.springBoot.starter.data.neo4j)
    api(libs.graphqlKotlin.server)
    api(libs.kotlinx.coroutines.reactor)
    api(libs.kotlin.reflect) {
        version {
            strictly(libs.versions.kotlin.get())
        }
    }
}

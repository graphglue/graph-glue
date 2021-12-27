import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

description = "A framework to connect graphql-kotlin and neo4j"

val reactorVersion = "5.3.10"
val graphqlKotlinVersion = "5.3.1"
val neo4jVersion = "2.6.2"

plugins {
    id("org.springframework.boot") version "2.5.5"
	id("io.spring.dependency-management") version "1.0.11.RELEASE"
	kotlin("jvm") version "1.6.10"
	kotlin("plugin.spring") version "1.6.10"
    id("com.expediagroup.graphql") version "5.3.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.expediagroup", "graphql-kotlin-spring-server",graphqlKotlinVersion)
    implementation("com.expediagroup", "graphql-kotlin-hooks-provider", graphqlKotlinVersion)
    implementation("org.springframework.boot", "spring-boot-starter-data-neo4j", neo4jVersion)
    implementation("org.springframework.data", "spring-data-neo4j", "6.2.0")
}

graphql {
    schema {
        packages = listOf("de.nkcoding.testing")
    }
}

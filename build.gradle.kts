description = "A framework to connect graphql-kotlin and neo4j"

val reactorVersion = "5.3.10"
val graphqlKotlinVersion = "5.3.1"
val neo4jVersion = "2.6.2"

plugins {
	kotlin("jvm") version "1.6.10"
	kotlin("plugin.spring") version "1.6.10"
    id("maven-publish")
}

repositories {
    mavenCentral()
}

dependencies {
    api("com.expediagroup", "graphql-kotlin-spring-server",graphqlKotlinVersion)
    api("org.springframework.boot", "spring-boot-starter-data-neo4j", neo4jVersion)
}

tasks {
    jar {
        enabled = true
    }

    val jarComponent = project.components.getByName("java")
    val sourcesJar by registering(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                groupId = "de.graphglue"
                artifactId = "graphglue"
                version = "0.1"

                from(jarComponent)
                artifact(sourcesJar.get())
            }
        }
    }
}
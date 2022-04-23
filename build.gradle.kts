import org.jetbrains.dokka.gradle.DokkaTask

description = "A library to develop annotation-based code-first GraphQL servers using GraphQL Kotlin, Spring Boot and Neo4j"
version = "1.1.1-SNAPSHOT"
group = "io.github.graphglue"

val graphqlKotlinVersion = "5.3.2"
val springBootVersion = "2.6.6"

plugins {
	kotlin("jvm") version "1.6.20"
	kotlin("plugin.spring") version "1.6.20"
    id("maven-publish")
    id("org.jetbrains.dokka") version "1.6.20"
    signing
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.springframework.boot", "spring-boot-starter-webflux", springBootVersion)
    api("com.expediagroup", "graphql-kotlin-spring-server", graphqlKotlinVersion)
    api("org.springframework.boot", "spring-boot-starter-data-neo4j", springBootVersion)
}

extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")

tasks {
    jar {
        enabled = true
    }

    fun configureDokka(builder: Action<org.jetbrains.dokka.gradle.GradleDokkaSourceSetBuilder>) {
        dokkaJavadoc {
            dokkaSourceSets {
                configureEach(builder)
            }
        }
        dokkaHtml {
            dokkaSourceSets {
                configureEach(builder)
            }
        }
    }

    configureDokka {
        includeNonPublic.set(true)
    }

    val jarComponent = project.components.getByName("java")
    val sourcesJar by registering(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }
    val dokka = named("dokkaJavadoc", DokkaTask::class)
    val javadocJar by registering(Jar::class) {
        archiveClassifier.set("javadoc")
        from("$buildDir/dokka/javadoc")
        dependsOn(dokka)
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                artifactId = "graphglue"

                pom {
                    name.set("graph-glue")
                    description.set(project.description)
                    url.set("https://graphglue.github.io/graph-glue")

                    organization {
                        name.set("Software Quality and Architecture - University of Stuttgart")
                        url.set("https://www.iste.uni-stuttgart.de/sqa/")
                    }

                    developers {
                        developer {
                            name.set("Niklas Krieger")
                            email.set("niklas.krieger@iste.uni-stuttgart.de")
                            organization.set("Software Quality and Architecture - University of Stuttgart")
                            organizationUrl.set("https://www.iste.uni-stuttgart.de/sqa/")
                        }
                        developer {
                            name.set("Georg Reißner")
                            email.set("georg.reissner@iste.uni-stuttgart.de")
                            organization.set("Software Quality and Architecture - University of Stuttgart")
                            organizationUrl.set("https://www.iste.uni-stuttgart.de/sqa/")
                        }
                        developer {
                            name.set("Christian Kurz")
                            email.set("chrikuvellberg@gmail.com")
                            organization.set("Software Quality and Architecture - University of Stuttgart")
                            organizationUrl.set("https://www.iste.uni-stuttgart.de/sqa/")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/graphglue/graph-glue.git")
                        developerConnection.set("scm:git:https://github.com/graphglue/graph-glue.git")
                        url.set("https://github.com/graphglue/graph-glue/tree/main")
                    }

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                }

                from(jarComponent)
                artifact(sourcesJar.get())
                artifact(javadocJar.get())
            }
        }
    }

    signing {
        setRequired({
            (project.extra["isReleaseVersion"] as Boolean)
        })
        sign(publishing.publications["mavenJava"])
    }

    nexusPublishing {
        repositories {
            sonatype {
                nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
                snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            }
        }
    }
}
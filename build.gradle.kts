import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

plugins {
	kotlin("jvm")
	kotlin("plugin.spring")
    id("maven-publish")
    id("org.jetbrains.dokka")
    signing
    id("io.github.gradle-nexus.publish-plugin")
}

allprojects {
    buildscript {
        repositories {
            mavenCentral()
        }
    }
    repositories {
        mavenCentral()
    }
}

kotlin {
    jvmToolchain(17)
}

extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")

subprojects {
    val springBootVersion: String by project
    val graphqlKotlinVersion: String by project
    val kotlinxCoroutinesReactorVersion: String by project
    val kotlinVersion: String by project

    val currentProject = this

    apply(plugin = "kotlin")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "signing")

    dependencies {
        api("org.springframework.boot", "spring-boot-starter-json", springBootVersion)
        api("com.expediagroup", "graphql-kotlin-server", graphqlKotlinVersion)
        api("org.springframework.boot", "spring-boot-starter-data-neo4j", springBootVersion)
        api("org.jetbrains.kotlinx", "kotlinx-coroutines-reactor", kotlinxCoroutinesReactorVersion)
        api("org.jetbrains.kotlin", "kotlin-reflect") {
            version {
                strictly(kotlinVersion)
            }
        }
    }

    tasks {

        dokka {
            dokkaSourceSets.main {
                documentedVisibilities.set(VisibilityModifier.values().asIterable())
            }
        }

        val jarComponent = project.components.getByName("java")
        val sourcesJar by registering(Jar::class) {
            archiveClassifier.set("sources")
            from(sourceSets.main.get().allSource)
        }
        val javadocJar by registering(Jar::class) {
            archiveClassifier.set("javadoc")
            from(dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
        }

        publishing {
            publications {
                create<MavenPublication>("mavenJava") {
                    pom {
                        name.set("graph-glue")
                        description.set(project.description)
                        url.set("https://graphglue.github.io")

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

                        val mavenPom = this
                        afterEvaluate {
                            mavenPom.description.set(currentProject.description)
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
                (rootProject.extra["isReleaseVersion"] as Boolean)
            })
            sign(publishing.publications["mavenJava"])
        }
    }
}

dokka {
    dependencies {
        subprojects.forEach { dokka(it) }
    }
}

tasks {
    jar {
        enabled = true
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
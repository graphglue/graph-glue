import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial

plugins {
	kotlin("jvm")
	kotlin("plugin.spring")
    id("maven-publish")
    id("org.jetbrains.dokka") apply false
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

extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")

subprojects {
    val springBootVersion: String by project
    val graphqlKotlinVersion: String by project
    val kotlinxCoroutinesReactorVersion: String by project

    val currentProject = this

    apply(plugin = "kotlin")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "signing")

    dependencies {
        api("org.springframework.boot", "spring-boot-starter-webflux", springBootVersion)
        api("com.expediagroup", "graphql-kotlin-server", graphqlKotlinVersion)
        api("org.springframework.boot", "spring-boot-starter-data-neo4j", springBootVersion)
        api("org.jetbrains.kotlinx", "kotlinx-coroutines-reactor", kotlinxCoroutinesReactorVersion)
    }

    tasks {
        fun configureDokka(builder: Action<org.jetbrains.dokka.gradle.GradleDokkaSourceSetBuilder>) {
            val dokkaJavadoc by getting(DokkaTask::class) {
                dokkaSourceSets {
                    configureEach(builder)
                }
            }
            val dokkaHtmlPartial by getting(DokkaTaskPartial::class) {
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
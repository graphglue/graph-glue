import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

/**
 * Shared configuration for every published graph-glue module: Kotlin/Spring compilation,
 * Dokka documentation, and the Maven Central publication (including the deployment bundle
 * collected by the root project's `nmcp.aggregation`).
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.jetbrains.dokka")
    id("com.gradleup.nmcp")
    `maven-publish`
    signing
}

val isReleaseVersion = !version.toString().endsWith("SNAPSHOT")

kotlin {
    jvmToolchain(17)
}

dokka {
    dokkaSourceSets.main {
        documentedVisibilities.set(VisibilityModifier.entries.asIterable())
    }
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)

            pom {
                name.set("graph-glue")
                description.set(provider { project.description })
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
            }
        }
    }
}

signing {
    setRequired { isReleaseVersion }
    // In-memory keys keep CI from having to materialise a keyring file.
    val signingKey = providers.environmentVariable("SIGNING_KEY")
        .orElse(providers.gradleProperty("signingKey"))
    val signingPassword = providers.environmentVariable("SIGNING_PASSWORD")
        .orElse(providers.gradleProperty("signingPassword"))
    if (signingKey.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), signingPassword.orNull)
    }
    sign(publishing.publications["mavenJava"])
}

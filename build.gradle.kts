// Versions come from the version catalog via `buildSrc`, which already puts these
// plugins on the build classpath, so they are applied here without a version.
plugins {
    id("org.jetbrains.dokka")
    id("com.gradleup.nmcp.aggregation")
}

dokka {
    dependencies {
        subprojects.forEach { dokka(it) }
    }
}

nmcpAggregation {
    centralPortal {
        username = providers.gradleProperty("mavenCentralUsername")
            .orElse(providers.environmentVariable("MAVEN_CENTRAL_USERNAME"))
        password = providers.gradleProperty("mavenCentralPassword")
            .orElse(providers.environmentVariable("MAVEN_CENTRAL_PASSWORD"))
        // Upload the deployment, then release it manually from the portal.
        publishingType = "USER_MANAGED"
        publicationName = "graph-glue:$version"
    }
}

dependencies {
    subprojects.forEach { nmcpAggregation(project(it.path)) }
}

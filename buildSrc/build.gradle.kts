plugins {
    `kotlin-dsl`
}

/**
 * Resolves a plugin alias from the version catalog to the coordinates of its marker artifact,
 * so the plugin can be applied by id (without a version) from a precompiled script plugin.
 */
fun Provider<PluginDependency>.marker() = map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}"
}

dependencies {
    implementation(libs.plugins.kotlin.jvm.marker())
    implementation(libs.plugins.kotlin.spring.marker())
    implementation(libs.plugins.dokka.marker())
    implementation(libs.plugins.nmcp.marker())
    implementation(libs.plugins.nmcpAggregation.marker())
}

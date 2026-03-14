import org.gradle.api.plugins.JavaPluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    `maven-publish`
}

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    extensions.getByType(KotlinMultiplatformExtension::class.java).withSourcesJar()
}

pluginManager.withPlugin("java") {
    extensions.getByType(JavaPluginExtension::class.java).withSourcesJar()
}

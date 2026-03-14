import java.io.File
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun findRepoRoot(startDir: File): File {
    var current: File? = startDir
    while (current != null) {
        if (current.resolve("VERSION").isFile) {
            return current
        }
        current = current.parentFile
    }

    error("Unable to locate repository root from ${startDir.absolutePath}")
}

plugins {
    `maven-publish`
}

publishing {
    repositories {
        maven {
            name = "buildRepo"
            url = findRepoRoot(rootDir).resolve("build/repo").toURI()
        }
    }
}

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    extensions.getByType(KotlinMultiplatformExtension::class.java).withSourcesJar()
}

pluginManager.withPlugin("java") {
    extensions.getByType(JavaPluginExtension::class.java).withSourcesJar()

    if (!pluginManager.hasPlugin("java-gradle-plugin")) {
        val publishing = extensions.getByType(PublishingExtension::class.java)
        if (publishing.publications.withType(MavenPublication::class.java).isEmpty()) {
            publishing.publications.create("mavenJava", MavenPublication::class.java) {
                from(components.getByName("java"))
            }
        }
    }
}

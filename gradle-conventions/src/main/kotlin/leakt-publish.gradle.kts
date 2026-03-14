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

fun githubPackagesRepositoryPath(): String? =
    providers.gradleProperty("gpr.repo").orNull
        ?: System.getenv("GITHUB_PACKAGES_REPOSITORY")
        ?: System.getenv("GITHUB_REPOSITORY")

fun githubPackagesUsername(): String? =
    providers.gradleProperty("gpr.user").orNull
        ?: System.getenv("GITHUB_ACTOR")

fun githubPackagesToken(): String? =
    providers.gradleProperty("gpr.key").orNull
        ?: System.getenv("GITHUB_TOKEN")

plugins {
    `maven-publish`
}

publishing {
    repositories {
        maven {
            name = "buildRepo"
            url = findRepoRoot(rootDir).resolve("build/repo").toURI()
        }

        val githubRepo = githubPackagesRepositoryPath()
        val githubUser = githubPackagesUsername()
        val githubToken = githubPackagesToken()

        if (githubRepo != null && githubUser != null && githubToken != null) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/${githubRepo.lowercase()}")
                credentials {
                    username = githubUser
                    password = githubToken
                }
            }
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

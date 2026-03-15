import org.gradle.api.plugins.JavaPluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("leakt-common")
    id("org.jetbrains.kotlin.jvm")
}

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

extensions.configure<KotlinJvmProjectExtension> {
    jvmToolchain(17)
}

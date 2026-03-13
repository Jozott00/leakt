plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
}

group = "dev.jozott.leakt"
version = "0.1.0-SNAPSHOT"

dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        create("leakt") {
            id = "dev.jozott.leakt"
            implementationClass = "dev.jozott.leakt.gradle.LeaktPlugin"
            displayName = "Leakt plugin"
            description = "Enables LSan-backed per-test leak checks for Kotlin/Native test binaries."
        }
    }
}

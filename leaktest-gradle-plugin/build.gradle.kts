plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
}

group = "dev.jozott.leaktest"
version = "0.1.0-SNAPSHOT"

dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        create("leaktest") {
            id = "dev.jozott.leaktest"
            implementationClass = "dev.jozott.leaktest.gradle.LeakTestPlugin"
            displayName = "LeakTest plugin"
            description = "Enables LSan-backed per-test leak checks for Kotlin/Native test binaries."
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
}

group = "dev.jozott.leakt"
version = "0.1.0-SNAPSHOT"

subprojects {
    group = rootProject.group
    version = rootProject.version
}

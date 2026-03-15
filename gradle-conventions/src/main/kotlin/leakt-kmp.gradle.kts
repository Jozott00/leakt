import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("leakt-common")
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    explicitApi()

    jvmToolchain(17)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

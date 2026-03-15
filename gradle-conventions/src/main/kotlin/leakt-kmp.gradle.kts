import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    id("leakt-common")
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    explicitApi()

    jvmToolchain(17)

    targets.withType<KotlinJvmTarget>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }
}

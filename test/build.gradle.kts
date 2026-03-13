import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("dev.jozott.leakt")
}

kotlin {
    explicitApi()

    when (hostTargetName()) {
        "linuxX64" -> linuxX64()
        "macosX64" -> macosX64()
        "macosArm64" -> macosArm64()
        else -> error("Unsupported host for the example project")
    }

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }
    }
}

kotlin.targets.withType<KotlinNativeTarget>().configureEach {
    compilations.getByName("test").defaultSourceSet.dependsOn(kotlin.sourceSets.getByName("nativeTest"))
}

fun hostTargetName(): String {
    val osName = System.getProperty("os.name")
    val architecture = System.getProperty("os.arch")

    return when {
        osName == "Linux" -> "linuxX64"
        osName == "Mac OS X" && architecture == "aarch64" -> "macosArm64"
        osName == "Mac OS X" -> "macosX64"
        else -> error("This prototype currently supports Linux x64 and macOS x64/arm64 hosts.")
    }
}

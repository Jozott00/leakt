import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    explicitApi()
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    linuxX64()
    macosX64()
    macosArm64()

    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }
    }
}

kotlin.targets.withType<KotlinNativeTarget>().configureEach {
    compilations.getByName("main").defaultSourceSet.dependsOn(kotlin.sourceSets.getByName("nativeMain"))
    compilations.getByName("test").defaultSourceSet.dependsOn(kotlin.sourceSets.getByName("nativeTest"))
    compilations.getByName("main").cinterops.create("lsan") {
        defFile(project.file("src/nativeInterop/cinterop/lsan.def"))
        compilerOpts("-I${project.file("src/nativeInterop/cinterop").absolutePath}")
        packageName("dev.jozott.leakt.lsan")
    }
}

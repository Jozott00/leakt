@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("leakt-kmp")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    linuxX64 {
        compilations.getByName("main").cinterops.create("lsan") {
            defFile(project.file("src/nativeInterop/cinterop/lsan.def"))
            compilerOpts("-I${project.file("src/nativeInterop/cinterop").absolutePath}")
            packageName("dev.jozott.leakt.lsan")
        }
    }
    // Register the other KMP targets so the runtime API is available everywhere, even though
    // only linuxX64 currently has a real LeakSanitizer-backed implementation.
    registerFallbackTargets()

    sourceSets {
        val fallbackMain by creating {
            dependsOn(commonMain.get())
        }

        // Every non-linuxX64 main source set uses the fallback no-op actuals.
        matching {
            it.name.endsWith("Main") &&
                    it.name !in setOf("commonMain", "fallbackMain", "linuxX64Main")
        }.configureEach {
            dependsOn(fallbackMain)
        }
    }
}


fun KotlinMultiplatformExtension.registerFallbackTargets() {
    jvm()
    js {
        nodejs()
    }
    wasmJs {
        nodejs()
    }
    wasmWasi {
        nodejs()
    }

    androidNativeX64()
    androidNativeX86()
    androidNativeArm32()
    androidNativeArm64()

    linuxArm64()
    mingwX64()

    macosX64()
    macosArm64()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()

    watchosX64()
    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()
    watchosDeviceArm64()
}

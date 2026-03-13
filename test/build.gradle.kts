import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

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

val ffiLeaksBuildDir = layout.buildDirectory.dir("ffileaks")
val buildFfiLeaksNativeLib by tasks.registering {
    val cDir = project.file("src/nativeInterop/cinterop")
    val cFile = cDir.resolve("ffileaks.c")
    val headerFile = cDir.resolve("ffileaks.h")
    val outDir = ffiLeaksBuildDir.get().asFile
    val objFile = outDir.resolve("ffileaks.o")
    val libFile = outDir.resolve("libffileaks.a")

    inputs.file(cFile)
    inputs.file(headerFile)
    outputs.file(libFile)

    doLast {
        outDir.mkdirs()
        val ccResult = ProcessBuilder(
            "cc",
            "-c",
            cFile.absolutePath,
            "-I${cDir.absolutePath}",
            "-fPIC",
            "-o",
            objFile.absolutePath,
        )
            .inheritIO()
            .start()
            .waitFor()
        check(ccResult == 0) { "Failed to compile ${cFile.absolutePath}" }

        val arResult = ProcessBuilder(
            "ar",
            "rcs",
            libFile.absolutePath,
            objFile.absolutePath,
        )
            .inheritIO()
            .start()
            .waitFor()
        check(arResult == 0) { "Failed to archive ${libFile.absolutePath}" }
    }
}

kotlin.targets.withType<KotlinNativeTarget>().configureEach {
    compilations.getByName("test").apply {
        defaultSourceSet.dependsOn(kotlin.sourceSets.getByName("nativeTest"))

        cinterops.create("ffileaks") {
            defFile(project.file("src/nativeInterop/cinterop/ffileaks.def"))
            compilerOpts("-I${project.file("src/nativeInterop/cinterop").absolutePath}")
            packageName("dev.jozott.leakt.test.ffileaks")
        }
    }

    binaries.all {
        if (name.contains("test", ignoreCase = true)) {
            linkerOpts(
                "-L${ffiLeaksBuildDir.get().asFile.absolutePath}",
                "-lffileaks",
            )
        }
    }
}

tasks.withType(KotlinNativeLink::class.java).configureEach {
    if (name.contains("Test", ignoreCase = true)) {
        dependsOn(buildFfiLeaksNativeLib)
    }
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

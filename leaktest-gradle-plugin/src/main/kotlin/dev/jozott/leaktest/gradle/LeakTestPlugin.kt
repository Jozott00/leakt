package dev.jozott.leaktest.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import java.io.File

class LeakTestPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

            configureRuntimeDependency(project, kotlin)
            configureNativeTargets(kotlin)
            configureNativeLinks(project)
            configureNativeTests(project)
        }
    }

    private fun configureRuntimeDependency(
        project: Project,
        kotlin: KotlinMultiplatformExtension,
    ) {
        val runtimeDependency = project.rootProject.findProject(":leaktest-runtime")?.let {
            project.dependencies.project(mapOf("path" to ":leaktest-runtime"))
        } ?: "dev.jozott.leaktest:leaktest-runtime:${project.version}"

        kotlin.sourceSets.matching { it.name.endsWith("Test") }.all { sourceSet ->
            project.dependencies.add(sourceSet.implementationConfigurationName, runtimeDependency)
        }
    }

    private fun configureNativeTargets(kotlin: KotlinMultiplatformExtension) {
        kotlin.targets.withType(KotlinNativeTarget::class.java).all { target ->
            target.binaries.all { binary ->
                binary.freeCompilerArgs = binary.freeCompilerArgs + sanitizerCompilerArgs(target)
                if (target.konanTarget.name.lowercase().startsWith("macos_")) {
                    val runtime = resolveBundledAsanRuntimePath()
                    binary.linkerOpts(runtime.absolutePath, "-rpath", runtime.parentFile.absolutePath)
                }
            }
        }
    }

    private fun configureNativeTests(project: Project) {
        project.tasks.withType(KotlinNativeTest::class.java).configureEach { task ->
            if (task.name.contains("Macos", ignoreCase = true)) {
                task.environment("ASAN_OPTIONS", "halt_on_error=0")
            } else {
                task.environment("ASAN_OPTIONS", "detect_leaks=1:halt_on_error=0:leak_check_at_exit=0")
            }
            task.environment("LSAN_OPTIONS", "exitcode=0")
        }
    }

    private fun sanitizerCompilerArgs(target: KotlinNativeTarget): List<String> {
        val konanTarget = target.konanTarget.name.lowercase()

        val clangOptions = when (konanTarget) {
            "macos_arm64" -> listOf(
                "-cc1",
                "-emit-obj",
                "-disable-llvm-passes",
                "-x",
                "ir",
                "-O0",
                "-mllvm",
                "-fast-isel=false",
                "-mllvm",
                "-global-isel=false",
                "-fsanitize=address",
            )
            "macos_x64" -> listOf(
                "-cc1",
                "-emit-obj",
                "-disable-llvm-passes",
                "-x",
                "ir",
                "-O0",
                "-fsanitize=address",
            )
            "linux_x64" -> listOf(
                "-cc1",
                "-emit-obj",
                "-disable-llvm-optzns",
                "-x",
                "ir",
                "-ffunction-sections",
                "-fdata-sections",
                "-O0",
                "-fsanitize=address",
            )
            else -> listOf("-fsanitize=address")
        }

        return listOf("-Xoverride-clang-options=${clangOptions.joinToString(",")}")
    }

    private fun configureNativeLinks(project: Project) {
        project.tasks.withType(KotlinNativeLink::class.java).configureEach { task ->
            if (task.name.contains("Macos", ignoreCase = true)) {
                task.doFirst {
                    installMacOsAsanRuntime()
                }
            }
        }
    }

    private fun installMacOsAsanRuntime() {
        val sourceRuntime = resolveXcodeAsanRuntime()
        val destinationRuntime = resolveBundledAsanRuntimePath()

        if (!destinationRuntime.exists()) {
            destinationRuntime.parentFile.mkdirs()
            sourceRuntime.copyTo(destinationRuntime, overwrite = false)
        }
    }

    private fun resolveXcodeAsanRuntime(): File {
        val runtimeRoot = File("/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib/clang")
        val latestResourceDir = runtimeRoot
            .listFiles { file -> file.isDirectory }
            ?.maxByOrNull { it.name }
            ?: error("Unable to locate the Xcode clang resource directory under ${runtimeRoot.absolutePath}")

        return latestResourceDir.resolve("lib/darwin/libclang_rt.asan_osx_dynamic.dylib")
    }

    private fun resolveBundledAsanRuntimePath(): File {
        val dependenciesRoot = File(File(System.getProperty("user.home")), ".konan/dependencies")
        val llvmRoot = dependenciesRoot
            .listFiles { file -> file.isDirectory && file.name.startsWith("llvm-") && file.name.contains("macos-essentials") }
            ?.maxByOrNull { it.name }
            ?: error("Unable to locate the bundled Kotlin/Native LLVM directory under ${dependenciesRoot.absolutePath}")
        val clangRoot = llvmRoot.resolve("lib/clang")
        val latestVersionDir = clangRoot
            .listFiles { file -> file.isDirectory }
            ?.maxByOrNull { it.name }
            ?: error("Unable to locate the bundled clang resource directory under ${clangRoot.absolutePath}")

        return latestVersionDir.resolve("lib/darwin/libclang_rt.asan_osx_dynamic.dylib")
    }
}

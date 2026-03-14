package dev.jozott.leakt.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import java.io.File

class LeaktPlugin : Plugin<Project>, KotlinCompilerPluginSupportPlugin {
    private val leaktVersion: String by lazy(LazyThreadSafetyMode.NONE) {
        javaClass
            .getResourceAsStream("/dev/jozott/leakt/gradle/leakt.properties")
            ?.use { stream ->
                java.util.Properties().apply { load(stream) }.getProperty("version")
            }
            ?.takeIf { it.isNotBlank() }
            ?: error("Missing compiler plugin version metadata")
    }

    override fun apply(project: Project) {
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

            // The Gradle plugin has two responsibilities:
            // 1. make the Leakt runtime available to test source sets
            // 2. wire Linux native test binaries/tasks for LeakSanitizer execution
            configureRuntimeDependency(project, kotlin)
            configureNativeTargets(kotlin)
            configureNativeTests(project)
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val target = kotlinCompilation.target as? KotlinNativeTarget ?: return false
        // The prototype currently only supports Linux native test compilations.
        return target.konanTarget.name.lowercase() == "linux_x64" && kotlinCompilation.name == "test"
    }

    override fun getCompilerPluginId(): String = "dev.jozott.leakt"

    override fun getPluginArtifact(): SubpluginArtifact {
        return SubpluginArtifact(
            groupId = "dev.jozott.leakt",
            artifactId = "compiler-plugin",
            version = leaktVersion
        )
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        return kotlinCompilation.target.project.provider { emptyList() }
    }

    private fun configureRuntimeDependency(
        project: Project,
        kotlin: KotlinMultiplatformExtension,
    ) {
        val runtimeDependency = "dev.jozott.leakt:core:$leaktVersion"

        // Add the runtime to every test source set so both manual `withLeakCheck` calls and
        // compiler-rewritten `@LeakCheck` usages can resolve the runtime symbols.
        kotlin.sourceSets.matching { it.name.endsWith("Test") }.all { sourceSet ->
            project.dependencies.add(sourceSet.implementationConfigurationName, runtimeDependency)
        }
    }

    private fun configureNativeTargets(kotlin: KotlinMultiplatformExtension) {
        kotlin.targets.withType(KotlinNativeTarget::class.java).all { target ->
            val isLinuxX64 = target.konanTarget.name.lowercase() == "linux_x64"
            target.binaries.all { binary ->
                val isTestBinary = binary.name.contains("test", ignoreCase = true)
                if (isLinuxX64 && isTestBinary) {
                    // Kotlin/Native links the final executable via ld.lld, not via the Clang
                    // driver, so `-fsanitize=address` is not enough to pull in the sanitizer
                    // runtime automatically. We therefore add the bundled ASan runtime archive
                    // explicitly for Linux test binaries.
                    binary.linkerOpts(*sanitizerLinkerArgs(target).toTypedArray())
                }
            }
        }
    }

    private fun configureNativeTests(project: Project) {
        project.tasks.withType(KotlinNativeTest::class.java).configureEach { task ->
            if (!task.name.contains("LinuxX64", ignoreCase = true)) return@configureEach
            // Leak checking is performed explicitly through the Leakt runtime, so disable the
            // default process-exit leak check and keep test failures reportable in-process.
            task.environment("ASAN_OPTIONS", "detect_leaks=1:halt_on_error=0:leak_check_at_exit=0")
            task.environment("LSAN_OPTIONS", "exitcode=0")
        }
    }

    private fun sanitizerLinkerArgs(target: KotlinNativeTarget): List<String> {
        val konanTarget = target.konanTarget.name.lowercase()

        return buildList {
            if (konanTarget == "linux_x64") {
                add(resolveBundledLinuxAsanRuntimePath().absolutePath)
            }
        }
    }

    private fun resolveBundledLinuxAsanRuntimePath(): File {
        // LeakSanitizer is provided through the AddressSanitizer runtime on Linux.
        return resolveBundledLinuxClangRuntimeDir().resolve("libclang_rt.asan.a")
    }

    private fun resolveBundledLinuxClangRuntimeDir(): File {
        // Kotlin/Native ships its own LLVM toolchain under ~/.konan/dependencies. Reuse that
        // bundled runtime so the linked sanitizer matches the compiler toolchain in use.
        val dependenciesRoot = File(File(System.getProperty("user.home")), ".konan/dependencies")
        val llvmRoot = dependenciesRoot
            .listFiles { file -> file.isDirectory && file.name.startsWith("llvm-") && file.name.contains("linux-essentials") }
            ?.maxByOrNull { it.name }
            ?: error("Unable to locate the bundled Linux Kotlin/Native LLVM directory under ${dependenciesRoot.absolutePath}")
        val clangRoot = llvmRoot.resolve("lib/clang")
        val latestVersionDir = clangRoot
            .listFiles { file -> file.isDirectory }
            ?.maxByOrNull { it.name }
            ?: error("Unable to locate the bundled clang resource directory under ${clangRoot.absolutePath}")
        val runtimeDir = latestVersionDir.resolve("lib/x86_64-unknown-linux-gnu")

        if (!runtimeDir.isDirectory) {
            error("Unable to locate the Linux sanitizer runtime directory under ${latestVersionDir.absolutePath}")
        }

        return runtimeDir
    }
}

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("leakt-kmp")
}

kotlin {
    explicitApi()
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    applyDefaultHierarchyTemplate()
    linuxX64 {
        compilations.getByName("main").cinterops.create("lsan") {
            defFile(project.file("src/nativeInterop/cinterop/lsan.def"))
            compilerOpts("-I${project.file("src/nativeInterop/cinterop").absolutePath}")
            packageName("dev.jozott.leakt.lsan")
        }
    }

    sourceSets {
        val fallbackMain by creating {
            dependsOn(commonMain.get())
        }

        listOf(jvmMain, webMain, mingwMain, appleMain, linuxArm64Main).forEach {
            it { dependsOn(fallbackMain) }
        }
    }
}

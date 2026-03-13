plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xsuppress-version-warnings")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

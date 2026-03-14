import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    `java-gradle-plugin`
    id("leakt-jvm")
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("projectVersion", project.version.toString())
    filesMatching("dev/jozott/leakt/gradle/leakt.properties") {
        expand("version" to project.version.toString())
    }
}

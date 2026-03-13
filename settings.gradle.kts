pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("gradle-plugin")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "leakt"

include(":core")
include(":test")

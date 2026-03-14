plugins {
    id("leakt-common")
}

subprojects {
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("dev.jozott.leakt:core")).using(project(":core"))
            substitute(module("dev.jozott.leakt:compiler-plugin")).using(project(":compiler-plugin"))
        }
    }
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "resurge"

include(
    ":app",
    ":core:model",
    ":core:designsystem",
    ":core:data",
    ":feature:auth",
    ":feature:profile",
)

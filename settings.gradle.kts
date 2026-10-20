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
    ":core:healthconnect",
    ":core:scale",
    ":feature:auth",
    ":feature:coach",
    ":feature:dashboard",
    ":feature:habits",
    ":feature:fitness",
    ":feature:food",
    ":feature:weight",
    ":feature:progress",
    ":feature:profile",
    ":feature:scale",
)

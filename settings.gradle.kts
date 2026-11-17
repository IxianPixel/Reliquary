pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// The desktop packaging needs a JDK 21 with jpackage; Gradle fetches one if it's missing.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Reliquary"

include(":app")
include(":desktop")

include(":core:model")
include(":core:network")
include(":core:database")
include(":core:data")
include(":core:sync")
include(":core:download")
include(":core:designsystem")
include(":core:ui")
include(":core:shell")

include(":feature:onboarding")
include(":feature:home")
include(":feature:channels")
include(":feature:playlists")
include(":feature:player")
include(":feature:downloads")
include(":feature:queue")
include(":feature:search")
include(":feature:settings")

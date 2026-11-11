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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Reliquary"

include(":app")

include(":core:model")
include(":core:network")
include(":core:database")
include(":core:data")
include(":core:sync")
include(":core:download")
include(":core:designsystem")
include(":core:ui")

include(":feature:onboarding")
include(":feature:home")
include(":feature:channels")
include(":feature:playlists")
include(":feature:player")
include(":feature:downloads")
include(":feature:search")
include(":feature:settings")

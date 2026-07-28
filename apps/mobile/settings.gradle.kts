pluginManagement {
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

rootProject.name = "PocketMind"
val pocketMindServerOnly = providers
    .environmentVariable("POCKETMIND_SERVER_ONLY")
    .orNull
    ?.equals("true", ignoreCase = true)
    ?: false

if (!pocketMindServerOnly) {
    include(":app")
}
include(":shared")
include(":assistant-service")
project(":assistant-service").projectDir = file("../../services/assistant")

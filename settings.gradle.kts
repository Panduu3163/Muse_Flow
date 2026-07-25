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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // NewPipeExtractor, pulled in by :innertube, is published on JitPack rather than Maven Central.
    maven { setUrl("https://jitpack.io") }
  }
}

rootProject.name = "MuseFlow"

include(":app")

// YouTube/InnerTube API client, ported from Echo-Music (GPL-3.0).
include(":innertube")

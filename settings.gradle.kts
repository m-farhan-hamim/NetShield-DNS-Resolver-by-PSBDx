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

// Settings-script classpath hardening for Dependabot (GHSA-xxph-c9ww-hj94). This is the
// classpath that loads the plugins declared below, and Dependabot reports its transitive
// dependencies against settings.gradle.kts. A constraint only raises the version of a module
// that is already on the classpath; it never adds a library, and none of this is packaged
// into the APK. The same pin for the project build scripts lives in build.gradle.kts.
buildscript {
  repositories { mavenCentral() }
  dependencies {
    constraints {
      add("classpath", "com.google.guava:guava:33.7.2-jre") { because("Dependabot GHSA-xxph-c9ww-hj94, fixed in 33.7.2") }
    }
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "DNS Resolver"

include(":app")

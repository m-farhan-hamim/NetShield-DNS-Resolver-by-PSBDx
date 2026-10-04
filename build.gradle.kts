// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Build-script classpath hardening for Dependabot alerts.
//
// The libraries below are build-time tooling that the Android Gradle Plugin pulls in
// transitively (Netty, Bouncy Castle, ...). They are never packaged into the APK, and
// nothing here adds a library that was not already on the classpath: a constraint only
// raises the version of a module that is already present. Each version is the lowest
// release that clears the advisories (Bouncy Castle and httpclient: latest in their
// line). Drop an entry once the AGP version in gradle/libs.versions.toml ships a
// patched version by itself.
buildscript {
  repositories {
    google()
    mavenCentral()
  }
  dependencies {
    // Netty: a BOM keeps every netty-* module on the same patched release.
    classpath(platform("io.netty:netty-bom:4.1.137.Final"))

    // Kotlin: AGP 9.1.1 bundles Kotlin Gradle Plugin 2.2.10. The BOM keeps the plugin, its API and
    // the stdlib aligned on the first stable release that fixes the build-cache deserialization
    // advisory. This crosses two Kotlin minors, so it is a separate commit: if CI objects,
    // revert just this one.
    classpath(platform("org.jetbrains.kotlin:kotlin-bom:2.4.20"))

    constraints {
      // Bouncy Castle: keep the provider, PKIX and util artifacts on one version.
      add("classpath", "org.bouncycastle:bcprov-jdk18on:1.86") { because("Dependabot: Bouncy Castle advisories fixed in 1.85+") }
      add("classpath", "org.bouncycastle:bcpkix-jdk18on:1.86") { because("Dependabot: Bouncy Castle advisories fixed in 1.84+") }
      add("classpath", "org.bouncycastle:bcutil-jdk18on:1.86") { because("must match bcpkix/bcprov") }

      add("classpath", "org.bitbucket.b_c:jose4j:0.9.6") { because("Dependabot: DoS via compressed JWE content") }
      add("classpath", "org.jdom:jdom2:2.0.6.1") { because("Dependabot: XXE injection in JDOM") }
      add("classpath", "org.apache.commons:commons-lang3:3.18.0") { because("Dependabot: uncontrolled recursion on long inputs") }
      add("classpath", "org.apache.httpcomponents:httpclient:4.5.14") { because("Dependabot: XSS in Apache HttpClient (fixed in 4.5.13)") }
    }
  }
}

plugins {
  alias(libs.plugins.android.application) apply false
}

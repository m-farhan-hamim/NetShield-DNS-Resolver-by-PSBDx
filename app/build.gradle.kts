plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.netshield.dns.psbdx"
    minSdk = 24
    targetSdk = 36
    versionCode = 7
    versionName = "3.1.2"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    // Release signing is optional at build time: if no keystore is present
    // (a clean local checkout, a PR build with no access to secrets, or
    // F-Droid's own build servers, which sign reproducible builds
    // themselves), the release build type simply falls back to being
    // unsigned instead of failing the build. In CI, the release.keystore.jks
    // file is decoded from the KEYSTORE_BASE64 GitHub secret just before
    // this runs - see .github/workflows/build.yml.
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/release.keystore.jks"
      if (file(keystorePath).exists()) {
        storeFile = file(keystorePath)
        storePassword = System.getenv("RELEASE_STORE_PASSWORD")
        keyAlias = System.getenv("RELEASE_KEYALIAS")
        keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      // R8 shrinking + resource shrinking for a smaller, optimized release
      // APK; see proguard-rules.pro.
      isMinifyEnabled = true
      isShrinkResources = true
      // Reproducible builds (F-Droid): AGP would otherwise embed the Git remote/revision of the
      // checkout in META-INF, which differs between our CI and F-Droid's build server.
      vcsInfo.include = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val releaseKeystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/release.keystore.jks"
      if (file(releaseKeystorePath).exists()) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
      if (file("${rootDir}/debug.keystore").exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    buildConfig = true
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Reproducible builds (F-Droid): AGP merges the baseline profiles that AndroidX libraries ship into
// assets/dexopt/baseline.prof, and R8 lays out classes*.dex using that profile. Both come out
// different on different build machines, so F-Droid's rebuild would not match the published APK.
// Dropping the profile only costs some first-launch startup speed.
tasks.configureEach {
  if (name.contains("ArtProfile")) {
    enabled = false
  }
}

// Every dependency below is plain, FOSS-licensed AndroidX/Google Material -
// no Firebase, no Google Play Services, no other proprietary or non-free
// dependencies, so the app builds cleanly on F-Droid's build servers.
dependencies {
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.core.ktx)

  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.runner)
}

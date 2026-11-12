import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(37) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.museflow.kqfzyw"
    minSdk = 24
    targetSdk = 37
    versionCode = 62
    versionName = "1.8.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Existing sideloaded MuseFlow installs use this certificate. A new upload key would
      // prevent an in-place update and discard access to their saved local data.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
    create("beta") {
      initWith(getByName("debug"))
      // Keep beta performance and resource shrinking aligned with the release build.
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Separate applicationId so this installs alongside any existing debug/release MuseFlow
      // build as its own app icon, rather than overwriting it.
      applicationIdSuffix = ".beta"
      versionNameSuffix = "-beta"
      matchingFallbacks += listOf("debug")
      signingConfig = signingConfigs.getByName("debugConfig")
    }
    create("verification") {
      initWith(getByName("beta"))
      applicationIdSuffix = ".verification"
      versionNameSuffix = "-verification"
      matchingFallbacks += listOf("debug")
    }
  }
  compileOptions {
    // Required by :innertube, which uses java.time and other API 26+ library types while this
    // app still supports minSdk 24.
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  // Android resolves streams through native WebViews/Rhino APIs, not desktop JSR-223.
  // Keep the Rhino interpreter; exclude only its unsupported JVM service-registration resource.
  packaging.resources.excludes += "META-INF/services/javax.script.ScriptEngineFactory"
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Robolectric's Android SDK 36 sandbox requires Java 21, but the rest of this build
// runs on the same installed JDK 21. Android source and bytecode target Java 17.
// Point just the unit-test JVM at a toolchain-provisioned JDK 21 instead of the daemon JVM.
tasks.withType<Test>().configureEach {
  javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
  // Robolectric 4.17's Android shared-memory bridge calls this JDK-internal descriptor API.
  // Export is confined to the desktop test JVM; it changes no Android app permissions.
  jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
  // Robolectric's Android jar URL handling on Windows can misread spaces in the user profile.
  // A short-path override affects only the test JVM; normal builds and app storage are unchanged.
  System.getenv("MUSEFLOW_TEST_HOME")?.let { systemProperty("user.home", it) }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.haze.core)
  implementation(libs.haze.blur)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.datasource)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.coil.network)
  implementation(libs.coil.cache.control)
  implementation(libs.androidx.palette.ktx)
  implementation(libs.material.kolor)
  implementation(project(":innertube"))
  coreLibraryDesugaring(libs.desugaring)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Firebase Auth with Google Sign-In requires all of the following to be uncommented together.
  // If you are using Firebase Auth with other providers (e.g. Email/Password), you may only need
  // firebase-auth.
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.androidx.palette.ktx)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.jaudiotagger)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

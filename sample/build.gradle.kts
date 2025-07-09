plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
}

android {
  namespace = "com.anggrayudi.storage.sample"
  compileSdk = 35

  signingConfigs {
    val debugKeystore =
      file(
        "${System.getProperty("user.home")}${File.separator}.android${File.separator}debug.keystore"
      )
    getByName("debug") {
      keyAlias = "androiddebugkey"
      keyPassword = "android"
      storePassword = "android"
      storeFile = debugKeystore
    }
    create("release") {
      keyAlias = "androiddebugkey"
      keyPassword = "android"
      storePassword = "android"
      storeFile = debugKeystore
    }
  }

  defaultConfig {
    applicationId = "com.anggrayudi.storage.sample"
    minSdk = 21
    targetSdk = 35
    versionCode = 1
    versionName = rootProject.extra["VERSION_NAME"] as String
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    debug { signingConfig = signingConfigs.getByName("debug") }
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
  }

  buildFeatures {
    viewBinding = true
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions { jvmTarget = "11" }

  flavorDimensions += "libSource"
  productFlavors {
    create("local") { dimension = "libSource" }
    create("maven") {
      dimension = "libSource"
      configurations.all {
        // Check for updates every build
        resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS)
      }
    }
  }

  applicationVariants.forEach { variant ->
    variant.sourceSets.forEach {
      it.javaDirectories += files("build/generated/ksp/${variant.name}/kotlin")
    }
  }
}

dependencies {
  implementation(project(":storage-compose"))
  //  implementation("com.anggrayudi:storage-compose:${rootProject.extra["VERSION_NAME"]}")

  implementation(libs.androidx.core)
  implementation(libs.androidx.lifecycle.runtime)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.multidex)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.datastore)
  implementation(libs.androidx.preference)
  implementation(libs.material.icons.ext)

  implementation(libs.timber)
  implementation(libs.coroutines.android)
  implementation(libs.material.progress.bar)
  implementation(libs.material.dialogs.files)

  testImplementation(libs.junit)
  testImplementation(libs.coroutines.test)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlin.test)

  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)

  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}

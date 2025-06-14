plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.dokka)
  alias(libs.plugins.maven.publish)
}

android {
  namespace = "com.anggrayudi.storage"
  compileSdk = 35
  resourcePrefix = "ss_"

  defaultConfig {
    minSdk = 21
    consumerProguardFiles("consumer-rules.pro")
  }

  testOptions { targetSdk = 35 }
  lint { targetSdk = 35 }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  buildFeatures { buildConfig = true }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  kotlinOptions {
    jvmTarget = "11"
    // Support @JvmDefault
    freeCompilerArgs = listOf("-Xjvm-default=all", "-opt-in=kotlin.RequiresOptIn")
  }
}

dependencies {
  api(libs.androidx.core)
  api(libs.androidx.appcompat)
  api(libs.androidx.activity)
  api(libs.androidx.fragment)
  api(libs.androidx.document.file)
  implementation(libs.androidx.lifecycle.runtime)

  implementation(libs.coroutines.android)

  testImplementation(libs.junit)
  testImplementation(libs.coroutines.test)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.inline)
  testImplementation(libs.mockito.all)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.powermock.junit4)
  testImplementation(libs.powermock.api.mockito)
}

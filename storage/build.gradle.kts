import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("com.android.library")
  alias(libs.plugins.dokka)
  alias(libs.plugins.maven.publish)
}

android {
  namespace = "com.anggrayudi.storage"
  compileSdk = 37
  resourcePrefix = "ss_"

  defaultConfig {
    minSdk = 26
    consumerProguardFiles("consumer-rules.pro")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  testOptions { targetSdk = 37 }
  lint { targetSdk = 37 }

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

  kotlin {
    compilerOptions {
      jvmTarget = JvmTarget.JVM_11
      // Support @JvmDefault
      freeCompilerArgs =
        listOf("-Xjvm-default=all", "-opt-in=kotlin.RequiresOptIn", "-Xexplicit-api=warning")
    }
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

  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.coroutines.test)
  androidTestImplementation(libs.kotlin.test)
  androidTestImplementation(libs.androidx.test.uiautomator)
}

afterEvaluate {
    tasks.findByName("generateReleaseBuildConfig")?.enabled = false
    tasks.findByName("generateDebugBuildConfig")?.enabled = false
}

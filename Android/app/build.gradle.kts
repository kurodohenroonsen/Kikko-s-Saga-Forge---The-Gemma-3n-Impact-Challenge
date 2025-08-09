/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
  alias(libs.plugins.android.application)
  // Note: set apply to true to enable google-services (requires google-services.json).
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.protobuf)
  // BOURDON'S FIX: Suppression du plugin Hilt.
  // alias(libs.plugins.hilt.application)
  alias(libs.plugins.oss.licenses)
  kotlin("kapt") // KAPT est toujours nécessaire pour Parcelize, mais plus pour Hilt.
  id("kotlin-parcelize")
}

android {
  namespace = "be.heyman.android.ai.kikko"
  compileSdk = 35

  defaultConfig {
    applicationId = "be.heyman.android.ai.kikko"
    // BOURDON'S FIX: Augmentation du minSdk requise pour Gemini Nano.
    minSdk = 31
    targetSdk = 35
    versionCode = 1
    versionName = "1.0.4"

    // Needed for HuggingFace auth workflows.
    manifestPlaceholders["appAuthRedirectScheme"] = "be.heyman.android.ai.kikko.oauth"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
    freeCompilerArgs += "-Xcontext-receivers"
  }
  buildFeatures {
    compose = true
    buildConfig = true
    // BOURDON'S FIX: Activation explicite du View Binding
    viewBinding = true
  }
  aaptOptions {
    noCompress("tflite")
  }
}

dependencies {
  // BOURDON'S FIX: Suppression des dépendances Hilt pour WorkManager et Hilt en général.
  // implementation("androidx.hilt:hilt-work:1.2.0")
  // kapt("androidx.hilt:hilt-compiler:1.2.0")

  implementation("androidx.work:work-runtime-ktx:2.9.0")
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)

  // BOURDON'S ADDITION: Dépendances pour le pont entre Coroutines et les API asynchrones de Google
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.0")

  implementation(libs.androidx.compose.navigation)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.material.icon.extended)
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.datastore)
  implementation(libs.com.google.code.gson)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.mediapipe.tasks.text)
  implementation(libs.mediapipe.tasks.genai)
  // BOURDON'S ADDITION: Dépendance pour Gemini Nano (AICore)
  implementation("com.google.ai.edge.aicore:aicore:0.0.1-exp01")
  implementation(libs.mediapipe.tasks.imagegen)
  implementation(libs.commonmark)
  implementation(libs.richtext)
  implementation(libs.tflite)
  implementation(libs.tflite.gpu)
  implementation(libs.tflite.support)
  implementation(libs.camerax.core)
  implementation(libs.camerax.camera2)
  implementation(libs.camerax.lifecycle)
  implementation(libs.camerax.view)
  implementation(libs.openid.appauth)
  implementation(libs.androidx.splashscreen)
  implementation(libs.protobuf.javalite)
  // BOURDON'S FIX: Suppression des dépendances Hilt en général.
  // implementation(libs.hilt.android)
  // implementation(libs.hilt.navigation.compose)
  implementation(libs.play.services.oss.licenses)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.analytics)
  // BOURDON'S FIX: Suppression du kapt Hilt.
  // kapt(libs.hilt.android.compiler)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  // BOURDON'S FIX: Suppression de la dépendance Hilt pour les tests.
  // androidTestImplementation(libs.hilt.android.testing)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)

  // Vosk & JNA
  implementation("com.alphacephei:vosk-android:0.3.47@aar")
  implementation("net.java.dev.jna:jna:5.13.0@aar")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")

  // ML Kit
  implementation("com.google.mlkit:barcode-scanning:17.2.0")
  implementation("com.google.mlkit:text-recognition:16.0.1")
  implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
  implementation("com.google.mlkit:language-id:17.0.6")
  implementation("com.google.mlkit:translate:17.0.3")
  implementation("com.google.mlkit:object-detection:17.0.1")
  implementation("com.google.mlkit:object-detection-custom:17.0.2")
  implementation("com.google.mlkit:image-labeling:17.0.9")
  implementation("com.google.mlkit:image-labeling-custom:17.0.3")
  // BOURDON'S ADDITION: Dépendance pour l'API de description d'image GenAI de ML Kit
  implementation("com.google.mlkit:genai-image-description:1.0.0-beta1")


  // BOURDON'S ADDITION: Dépendances requises pour Google Nearby et Location
  implementation("com.google.android.gms:play-services-nearby:19.3.0")
  implementation("com.google.android.gms:play-services-location:21.3.0")

  // BOURDON'S ADDITION: Dépendances pour AndroidX Media3 (ExoPlayer)
  val media3Version = "1.4.0"
  implementation("androidx.media3:media3-exoplayer:$media3Version")
  implementation("androidx.media3:media3-ui:$media3Version")

  // Dépendances pour les layouts XML et AppCompat
  implementation("androidx.appcompat:appcompat:1.7.1")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")
  implementation("androidx.recyclerview:recyclerview:1.3.2")
  implementation("com.google.android.flexbox:flexbox:3.0.0")

  implementation("androidx.fragment:fragment-ktx:1.8.1")

  // BOURDON'S ADDITION: Dépendance pour Glide (chargement d'images)
  implementation("com.github.bumptech.glide:glide:4.16.0")

}

protobuf {
  protoc { artifact = "com.google.protobuf:protoc:4.26.1" }
  generateProtoTasks { all().forEach { it.plugins { create("java") { option("lite") } } } }
}
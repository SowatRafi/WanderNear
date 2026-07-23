plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.wandernear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wandernear"
        minSdk = 31          // Android 12 — required by the LiteRT-LM on-device AI runtime
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    // Java target 17 (runs on Android Studio's bundled Java 21).
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    // Kotlin bytecode target (new compilerOptions DSL, Kotlin 2.x).
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // The Compose "bill of materials" keeps all Compose libraries on matching versions.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    // Room — the traveller's private, editable journal database.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // Coil — loads the traveller's local photos into Compose (thumbnails + viewer).
    implementation(libs.coil.compose)
    // WorkManager — runs the daily anniversary check in the background.
    implementation(libs.androidx.work.runtime)
    // LiteRT-LM — Google's on-device LLM runtime (runs Gemma 4 E2B).
    implementation(libs.litertlm.android)
    // Vosk — offline speech-to-text (bundled model, no internet, no hallucination).
    implementation(libs.vosk.android)
    // JVM unit tests (the AI grounding guardrail + trick-tests).
    testImplementation(libs.junit)
}

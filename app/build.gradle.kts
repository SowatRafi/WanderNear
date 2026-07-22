plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.wandernear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wandernear"
        minSdk = 26          // Android 8.0 — covers virtually all active phones
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    // Java/Kotlin target 17 (runs on Android Studio's bundled Java 21).
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
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
}

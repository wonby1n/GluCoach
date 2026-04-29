plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.ssafy.s309.feature.glucofit"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-rc01")

    // Samsung Health Data SDK (로컬 AAR)
    implementation(files("libs/samsung-health-data-api-1.1.0.aar"))

    // Samsung Health SDK 의존성
    implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime:${libs.versions.kotlin.get()}")
    implementation("com.google.code.gson:gson:2.10.1")
}

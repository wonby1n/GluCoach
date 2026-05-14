plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.ssafy.s309"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ssafy.s309"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"https://k14s309.p.ssafy.io/\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "BASE_URL", "\"https://k14s309.p.ssafy.io/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

// feature-glucofit이 가져오는 Health Connect의 전체 Guava와
// CameraX 트랜지티브의 com.google.guava:listenablefuture:1.0이 ListenableFuture 클래스 중복을 일으킴.
// 표준 해법: listenablefuture를 9999.0(전체 Guava와 충돌 회피용 빈 stub) 으로 강제 → ListenableFuture는 Guava 측에서만 제공.
configurations.all {
    resolutionStrategy {
        force("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")
    }
}

dependencies {
    // GlucoFit 테스트 모듈 (Samsung Health + 커스텀 IME)
    implementation(project(":feature-glucofit"))

    // Guava: feature-glucofit의 Health Connect가 runtime classpath로만 가져와서
    // CameraX(compile time에 ListenableFuture 필요)가 클래스 미해결을 일으킴 → 명시적으로 추가.
    implementation("com.google.guava:guava:32.1.3-android")

    // Health Connect: feature-glucofit이 implementation으로 가져와서 app 모듈에서는
    // 클래스가 안 보임. HealthSourceScreen에서 PermissionController 권한 launcher를
    // 호출해야 하므로 app 측에도 직접 의존성 추가 (feature-glucofit과 동일 버전).
    implementation("androidx.health.connect:connect-client:1.1.0-rc01")

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt (DI)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Retrofit + OkHttp (HTTP)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coil (이미지 로딩)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)

    // kotlinx.serialization (JSON)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    // CameraX
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // BLE (FastBle)
    implementation(libs.fastble)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Vico Charts
    implementation("com.patrykandpatrick.vico:compose-m3:1.15.0")

    // Glance (홈화면 위젯)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // WorkManager + Hilt-Work (위젯 30분 주기 갱신)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Wear OS — 폰 → 워치(:wear) 혈당 송신용 (WearDataSender)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    // 테스트
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.tesladash"
    compileSdk = 35  // 👈 35로 업데이트

    defaultConfig {
        applicationId = "com.example.tesladash"
        minSdk = 26
        targetSdk = 35  // 👈 35로 업데이트
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17  // 👈 17로 업데이트
        targetCompatibility = JavaVersion.VERSION_17  // 👈 17로 업데이트
    }
    kotlinOptions {
        jvmTarget = "17"  // 👈 17로 업데이트
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // FCM (푸시 알림)
    implementation("com.google.firebase:firebase-messaging-ktx:24.0.3")  // 👈 버전 업데이트
    
    // Custom Tabs (OAuth 로그인용)
    implementation("androidx.browser:browser:1.8.0")
}

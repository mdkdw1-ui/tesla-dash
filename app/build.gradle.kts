plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.tesladash"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.tesladash"
        minSdk = 26
        targetSdk = 35
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // FCM (푸시 알림)
    implementation("com.google.firebase:firebase-messaging-ktx:24.0.3")
    
    // Custom Tabs (OAuth 로그인용)
    implementation("androidx.browser:browser:1.8.0")
}

// 📌 루트 폴더의 www/icon.png를 앱 아이콘(mipmap-hdpi/ic_launcher.png)으로 자동 복사
val copyWwwIcon = tasks.register<Copy>("copyWwwIcon") {
    from(rootProject.file("www/icon.png"))
    into("src/main/res/mipmap-hdpi")
    rename("icon.png", "ic_launcher.png")
}

tasks.named("preBuild") {
    dependsOn(copyWwwIcon)
}

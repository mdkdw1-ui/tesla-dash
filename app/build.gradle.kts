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

// 📌 www/icon.png를 모든 해상도에 복사하고, 기본 원형 아이콘(v26 XML)을 제거하는 태스크
val copyWwwIcon = tasks.register("copyWwwIcon") {
    doLast {
        val iconFile = rootProject.file("www/icon.png")
        if (iconFile.exists()) {
            // 1. 모든 해상도 폴더에 PNG 복사 (각종 기기 대응)
            val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
            densities.forEach { density ->
                val destDir = file("src/main/res/mipmap-$density")
                destDir.mkdirs()
                iconFile.copyTo(File(destDir, "ic_launcher.png"), overwrite = true)
                iconFile.copyTo(File(destDir, "ic_launcher_round.png"), overwrite = true)
            }

            // 2. 안드로이드 기본 적응형 원형 아이콘(v26 XML) 삭제 (PNG가 우선 적용되도록 함)
            val v26Dir = file("src/main/res/mipmap-anydpi-v26")
            if (v26Dir.exists()) {
                v26Dir.deleteRecursively()
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(copyWwwIcon)
}


tasks.named("preBuild") {
    dependsOn(copyWwwIcon)
}
